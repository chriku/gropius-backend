package gropius.service.template

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersionDetector
import gropius.dto.input.common.JSONFieldInput
import gropius.dto.input.ifPresent
import gropius.dto.input.template.CreateTemplatedNodeInput
import gropius.dto.input.template.UpdateTemplatedNodeInput
import gropius.model.template.BaseTemplate
import gropius.model.template.TemplatedNode
import org.springframework.stereotype.Service

/**
 * Service [TemplatedNode]s. Provides functions to create, update and delete
 *
 * @param objectMapper injected [ObjectMapper], used to parse [JSONFieldInput]
 */
@Service
class TemplatedNodeService(val objectMapper: ObjectMapper) {

    /**
     * Updates the [TemplatedNode.templatedFields] of a [TemplatedNode] based on the [input]
     * Does not check authorization permissions, and does not save the provided [node] afterwards
     * Validates the new value of the fiels
     *
     * @param node the node to update the templated fields of
     * @param input defines how to update the templated fields
     * @throws IllegalArgumentException if the new value for a templated field is invalid
     */
    suspend fun updateTemplatedFields(node: TemplatedNode, input: UpdateTemplatedNodeInput) {
        input.templatedFields.ifPresent {fields ->
            val template = node.template().value
            ensureTemplatedFieldsExist(template, fields.map { it.name })
            for (field in fields) {
                validateField(field.value as JsonNode, template.templateFieldSpecifications[field.name]!!, field.name)
                node.templatedFields[field.name] = objectMapper.writeValueAsString(field.value)
            }
        }
    }

    /**
     * Validates the initial input for [TemplatedNode.templatedFields]
     * If no input is provided for a field, `null` is assigned as default value
     *
     * @param template the initial associated template for the node to create
     * @param input input of the create mutation, defines the initial value for the templated fields
     * @return the initial value for [TemplatedNode.templatedFields]
     * @throws IllegalArgumentException if the value for a templated field is not compatible with its schema
     */
    suspend fun validateInitialTemplatedFields(
        template: BaseTemplate<*, *>,
        input: CreateTemplatedNodeInput
    ): MutableMap<String, String> {
        ensureTemplatedFieldsExist(template, input.templatedFields.map { it.name })
        val fieldLookup = input.templatedFields.associateBy { it.name }
        return template.templateFieldSpecifications.mapValues {
            val value: JsonNode = fieldLookup[it.key]?.value as JsonNode? ?: JsonNodeFactory.instance.nullNode()
            validateField(value, it.value, it.key)
            objectMapper.writeValueAsString(value)
        }.toMutableMap()
    }

    /**
     * Validates a value for a templated field
     *
     * @param value the new value for the templated field
     * @param schema JSON schema of the templated field obtained from the associated Template
     * @param name the name of the field
     * @throws IllegalArgumentException if the [schema] does not allow [value]
     */
    private fun validateField(value: JsonNode, schema: String, name: String) {
        val parsedSchema = objectMapper.readTree(schema)
        val validator = JsonSchemaFactory.getInstance(SpecVersionDetector.detect(parsedSchema)).getSchema(parsedSchema)
        val validationResult = validator.validate(value)
        if (validationResult.isNotEmpty()) {
            throw IllegalArgumentException("Invalid input for templated field $name: ${validationResult.map { it.message }}")
        }
    }

    /**
     * Ensures that all fields in [fields] are defined by [template]
     *
     * @param template the Template of the [TemplatedNode] where [fields] should be used
     * @param fields a list of templated field names to check for existence
     * @throws IllegalArgumentException if any field is not defined by [template]
     */
    private fun ensureTemplatedFieldsExist(template: BaseTemplate<*, *>, fields: Collection<String>) {
        for (field in fields) {
            if (field !in template.templateFieldSpecifications) {
                throw IllegalArgumentException("Unknown templated field $field")
            }
        }
    }
}