package gropius.model.template

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import gropius.model.common.NamedNode
import gropius.model.issue.Issue
import io.github.graphglue.model.Direction
import io.github.graphglue.model.DomainNode
import io.github.graphglue.model.FilterProperty
import io.github.graphglue.model.NodeRelationship
import org.springframework.data.annotation.Transient

@DomainNode
@GraphQLDescription("Type of an Issue like BUG or FEATURE_REQUEST. Part of an IssueTemplate.")
class IssueType(name: String, description: String) : NamedNode(name, description) {

    companion object {
        const val PART_OF = "PART_OF"
    }

    @NodeRelationship(Issue.TYPE, Direction.INCOMING)
    @GraphQLDescription("Issues with this type.")
    @FilterProperty
    @delegate:Transient
    val issuesWithType by NodeSetProperty<Issue>()

    @NodeRelationship(PART_OF, Direction.OUTGOING)
    @GraphQLDescription("IssueTemplates this is a part of.")
    @FilterProperty
    @delegate:Transient
    val partOf by NodeSetProperty<IssueTemplate>()

}