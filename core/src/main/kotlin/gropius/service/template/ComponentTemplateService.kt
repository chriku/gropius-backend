package gropius.service.template

import gropius.authorization.GropiusAuthorizationContext
import gropius.dto.input.template.CreateComponentTemplateInput
import gropius.model.template.ComponentTemplate
import gropius.model.template.ComponentVersionTemplate
import gropius.model.template.SubTemplate
import gropius.repository.template.ComponentTemplateRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Service

/**
 * Service for [ComponentTemplate]s. Provides functions to create, update and delete
 *
 * @param repository the associated repository used for CRUD functionality
 * @param componentTemplateRepository used to get [ComponentTemplate]s
 * @param subTemplateService used to create [SubTemplate]s
 */
@Service
class ComponentTemplateService(
    repository: ComponentTemplateRepository,
    private val componentTemplateRepository: ComponentTemplateRepository,
    private val subTemplateService: SubTemplateService
) : RelationPartnerTemplateService<ComponentTemplate, ComponentTemplateRepository>(repository) {

    /**
     * Creates a new [ComponentTemplate] based on the provided [input]
     * Checks the authorization status
     *
     * @param authorizationContext used to check for the required permission
     * @param input defines the [ComponentTemplate]
     * @return the saved created [ComponentTemplate]
     */
    suspend fun createComponentTemplate(
        authorizationContext: GropiusAuthorizationContext, input: CreateComponentTemplateInput
    ): ComponentTemplate {
        input.validate()
        checkCreateTemplatePermission(authorizationContext)
        val template = ComponentTemplate(input.name, input.description, mutableMapOf(), false)
        createdRelationPartnerTemplate(template, input)
        template.componentVersionTemplate().value = subTemplateService.createSubTemplate(::ComponentVersionTemplate,
            input.componentVersionTemplate,
            template.extends().map { it.componentVersionTemplate().value })
        template.possibleVisibleInterfaceSpecifications() += template.extends().flatMap {
            it.possibleVisibleInterfaceSpecifications()
        }
        template.possibleInvisibleInterfaceSpecifications() += template.extends().flatMap {
            it.possibleInvisibleInterfaceSpecifications()
        }
        return repository.save(template).awaitSingle()
    }

}