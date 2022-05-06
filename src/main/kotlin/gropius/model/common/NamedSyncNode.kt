package gropius.model.common

import com.expediagroup.graphql.generator.annotations.GraphQLDescription
import io.github.graphglue.model.DomainNode
import java.time.OffsetDateTime

@DomainNode
@GraphQLDescription("SyncNode with a name and description")
abstract class NamedSyncNode(
    createdAt: OffsetDateTime,
    lastModifiedAt: OffsetDateTime,
    @GraphQLDescription("The name of this entity.")
    override var name: String,
    @GraphQLDescription("The description of this entity.")
    override var description: String
) : SyncNode(createdAt, lastModifiedAt), Named