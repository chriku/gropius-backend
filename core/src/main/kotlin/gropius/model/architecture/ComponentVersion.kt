package gropius.model.architecture

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import gropius.model.user.permission.NodePermission
import io.github.graphglue.model.*
import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import gropius.model.template.*
import gropius.model.user.permission.ComponentPermission
import io.github.graphglue.model.property.NodeCache
import org.springframework.data.annotation.Transient
import org.springframework.data.neo4j.core.schema.CompositeProperty

@DomainNode
@GraphQLDescription(
    """Version of a component. 
    Can specifies visible/invisible InterfaceSpecifications.
    Can be used in Relations, affected by issues and included by Projects.
    READ is granted if READ is granted on `component`.
    """
)
@Authorization(NodePermission.READ, allowFromRelated = ["component", "includingProjects"])
@Authorization(NodePermission.ADMIN, allowFromRelated = ["component"])
@Authorization(ComponentPermission.RELATE_TO_COMPONENT, allowFromRelated = ["component"])
@Authorization(ComponentPermission.RELATE_FROM_COMPONENT, allowFromRelated = ["component"])
@Authorization(ComponentPermission.ADD_TO_PROJECTS, allowFromRelated = ["component"])
class ComponentVersion(
    name: String,
    description: String,
    @property:GraphQLDescription("The version of this ComponentVersion")
    @FilterProperty
    @OrderProperty
    override var version: String,
    @property:GraphQLIgnore
    @CompositeProperty
    override val templatedFields: MutableMap<String, String>
) : RelationPartner(name, description), Versioned, MutableTemplatedNode {

    @NodeRelationship(BaseTemplate.USED_IN, Direction.INCOMING)
    @GraphQLDescription("The Template of this ComponentVersion")
    @FilterProperty
    @delegate:Transient
    override val template by NodeProperty<ComponentVersionTemplate>()

    @NodeRelationship(Component.VERSION, Direction.INCOMING)
    @GraphQLDescription("The Component which defines this ComponentVersions")
    @FilterProperty
    @delegate:Transient
    val component by NodeProperty<Component>()

    @NodeRelationship(Project.COMPONENT, Direction.INCOMING)
    @GraphQLDescription("Projects which include this ComponentVersion")
    @FilterProperty
    @delegate:Transient
    val includingProjects by NodeSetProperty<Project>()

    @NodeRelationship(InterfaceDefinition.COMPONENT_VERSION, Direction.INCOMING)
    @GraphQLDescription("InterfaceSpecificationVersions on this ComponentVersion.")
    @FilterProperty
    @delegate:Transient
    val interfaceDefinitions by NodeSetProperty<InterfaceDefinition>()

    @GraphQLIgnore
    override suspend fun relationPartnerTemplate(cache: NodeCache?): RelationPartnerTemplate<*, *> {
        return component(cache).value.template(cache).value
    }
}