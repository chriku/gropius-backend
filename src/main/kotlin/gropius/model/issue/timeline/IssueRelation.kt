package gropius.model.issue.timeline

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import gropius.model.issue.Issue
import gropius.model.template.IssueRelationType
import io.github.graphglue.model.Direction
import io.github.graphglue.model.DomainNode
import io.github.graphglue.model.FilterProperty
import io.github.graphglue.model.NodeRelationship
import org.springframework.data.annotation.Transient
import java.time.OffsetDateTime

@DomainNode
@GraphQLDescription(
    """Event representing that a relation between two Issues has been created.
    An IssueRelation is only active if it is still in `outgoingRelations` on the `issue`,
    respectively in incomingRelations on the `relatedIssue`.
    """
)
class IssueRelation(
    createdAt: OffsetDateTime,
    lastModifiedAt: OffsetDateTime,
) : TimelineItem(createdAt, lastModifiedAt) {

    companion object {
        const val TYPE = "TYPE"
        const val RELATED_ISSUE = "RELATED_ISSUE"
    }

    @NodeRelationship(TYPE, Direction.OUTGOING)
    @GraphQLDescription("The type of the relation, e.g. DUPLICATES. Allowed types are defined by the IssueTemplate.")
    @FilterProperty
    @delegate:Transient
    var type by NodeProperty<IssueRelationType?>()

    @NodeRelationship(RELATED_ISSUE, Direction.OUTGOING)
    @GraphQLDescription("The end of the relation.")
    @FilterProperty
    @delegate:Transient
    var relatedIssue by NodeProperty<Issue>()

}