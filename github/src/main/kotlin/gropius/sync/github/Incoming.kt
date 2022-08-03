package gropius.sync.github

import com.apollographql.apollo3.ApolloClient
import gropius.model.user.IMSUser
import gropius.sync.IssueCleaner
import gropius.sync.github.generated.fragment.IssueDataExtensive
import gropius.sync.github.generated.fragment.TimelineItemData
import gropius.sync.github.generated.fragment.TimelineItemData.Companion.asIssueComment
import gropius.sync.github.generated.fragment.TimelineItemData.Companion.asNode
import gropius.sync.github.model.IssueInfo
import gropius.sync.github.model.TimelineEventInfo
import gropius.sync.github.model.TimelineItemDataCache
import gropius.sync.github.repository.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.mongodb.core.ReactiveMongoOperations
import org.springframework.data.mongodb.core.query
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Criteria.where
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.neo4j.core.ReactiveNeo4jOperations
import org.springframework.data.neo4j.core.findById
import org.springframework.stereotype.Component
import java.lang.Exception
import java.time.OffsetDateTime

/**
 * Stateless component for the incoming part of the sync
 * @param helper Reference for the spring instance of JsonHelper
 * @param imsConfigManager Reference for the spring instance of IMSConfigManager
 * @param neoOperations Reference for the spring instance of ReactiveNeo4jOperations
 * @param syncNotificator Reference for the spring instance of SyncNotificator
 */
@Component
class Incoming(
    /**
     * Reference for the spring instance of RepositoryInfoRepository
     */
    private val repositoryInfoRepository: RepositoryInfoRepository,
    /**
     * Reference for the spring instance of IssueInfoRepository
     */
    private val issueInfoRepository: IssueInfoRepository,
    /**
     * Reference for the spring instance of UserInfoRepository
     */
    private val userInfoRepository: UserInfoRepository,
    /**
     * Reference for the spring instance of LabelInfoRepository
     */
    private val labelInfoRepository: LabelInfoRepository,
    /**
     * Reference for the spring instance of ReactiveMongoOperations
     */
    private val mongoOperations: ReactiveMongoOperations,
    /**
     * Reference for the spring instance of ReactiveMongoOperations
     */
    private val timelineEventInfoRepository: TimelineEventInfoRepository,
    /**
     * Reference for the spring instance of IssueCleaner
     */
    private val issueCleaner: IssueCleaner,
    /**
     * Reference for the spring instance of NodeSourcerer
     */
    private val nodeSourcerer: NodeSourcerer,
    /**
     * Reference for the spring instance of TimelineItemHandler
     */
    private val timelineItemHandler: TimelineItemHandler,
    @Qualifier("graphglueNeo4jOperations")
    private val neoOperations: ReactiveNeo4jOperations,
    private val helper: JsonHelper,
    private val imsConfigManager: IMSConfigManager,
    private val syncNotificator: SyncNotificator
) {

    /**
     * Mark issue as dirty
     * @param info The full dataset of an issue
     * @return The DateTime the issue was last changed
     */
    suspend fun issueModified(imsProjectConfig: IMSProjectConfig, info: IssueDataExtensive): OffsetDateTime {
        val (neoIssue, mongoIssue) = nodeSourcerer.ensureIssue(imsProjectConfig.imsProject, info)
        mongoOperations.updateFirst(
            Query(where("_id").`is`(mongoIssue.id!!)), Update().set(IssueInfo::dirty.name, true), IssueInfo::class.java
        ).awaitSingle()
        return info.updatedAt
    }

    /**
     * Save a single timeline event into the database
     * @param issue the issue the timeline belongs to
     * @param event a single timeline item
     * @return The time of the event or null for error
     */
    suspend fun handleTimelineEvent(issue: IssueInfo, event: TimelineItemData): OffsetDateTime? {
        val dbEntry = timelineEventInfoRepository.findByGithubId(event.asNode()!!.id)
        return if ((dbEntry != null) && !((event.asIssueComment()?.lastEditedAt != null) && (event.asIssueComment()!!.lastEditedAt!! > dbEntry.lastModifiedAt))) {
            dbEntry.lastModifiedAt
        } else {
            val (neoId, time) = timelineItemHandler.handleIssueModifiedItem(issue, event)
            if (time != null) {
                timelineEventInfoRepository.save(TimelineEventInfo(event.asNode()!!.id, neoId, time, event.__typename))
                    .awaitSingle()
            }
            time
        }
    }

    /**
     * Sync github to gropius
     */
    suspend fun sync() {
        for (imsTemplate in imsConfigManager.findTemplates()) {
            for (ims in imsTemplate.usedIn()) {
                try {
                    val imsConfig = IMSConfig(helper, ims)
                    syncIMS(imsConfig)
                } catch (e: SyncNotificator.NotificatedError) {
                    syncNotificator.sendNotification(
                        ims, SyncNotificator.NotificationDummy(e)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun getGithubUserToken(imsUser: IMSUser): String {
        return System.getenv("GITHUB_DUMMY_PAT")!!//TODO: @modellbahnfreak!!
    }

    suspend fun syncIMS(imsConfig: IMSConfig) {
        val readUser = neoOperations.findById<IMSUser>(imsConfig.readUser)!!
        if (readUser.ims().value != imsConfig.ims) {
            throw SyncNotificator.NotificatedError("SYNC_GITHUB_USER_INVALID_IMS");
        }
        val apolloClient = ApolloClient.Builder().serverUrl("https://api.github.com/graphql")
            .addHttpHeader("Authorization", "bearer " + getGithubUserToken(readUser)).build()
        for (project in imsConfig.ims.projects()) {
            try {
                val imsProjectConfig = IMSProjectConfig(helper, imsConfig, project)
                syncProject(imsProjectConfig, apolloClient)
            } catch (e: SyncNotificator.NotificatedError) {
                syncNotificator.sendNotification(
                    project, SyncNotificator.NotificationDummy(e)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun syncProject(imsProjectConfig: IMSProjectConfig, apolloClient: ApolloClient) {
        imsProjectConfig.imsProject.trackable().value
        val issueGrabber = IssueGrabber(
            imsProjectConfig.repo,
            repositoryInfoRepository,
            mongoOperations,
            apolloClient,
            imsProjectConfig.imsProject.rawId!!
        )
        issueGrabber.requestNewNodes()
        issueGrabber.iterate {
            issueModified(imsProjectConfig, it)
        }
        for (issue in issueInfoRepository.findByProjectAndDirtyIsTrue(imsProjectConfig.imsProject.rawId!!).toList()) {
            val timelineGrabber = TimelineGrabber(
                issueInfoRepository, mongoOperations, issue.githubId, apolloClient, imsProjectConfig.imsProject.rawId!!
            )
            timelineGrabber.requestNewNodes()
        }
        for (issueId in mongoOperations.query<TimelineItemDataCache>().matching(
            Query.query(
                Criteria.where(TimelineItemDataCache::imsProject.name).`is`(imsProjectConfig.imsProject.rawId!!)
                    .`and`(TimelineItemDataCache::attempts.name).not().gte(7)
            )
        ).all().asFlow().map { it.issue }.toSet()) {
            val issue = issueInfoRepository.findByIMSProjectAndGithubId(imsProjectConfig.imsProject.rawId!!, issueId)!!
            try {
                val timelineGrabber = TimelineGrabber(
                    issueInfoRepository,
                    mongoOperations,
                    issue.githubId,
                    apolloClient,
                    imsProjectConfig.imsProject.rawId!!
                )
                timelineGrabber.iterate {
                    handleTimelineEvent(issue, it)
                }
                issueCleaner.cleanIssue(issue.neo4jId)
                mongoOperations.updateFirst(
                    Query(where("_id").`is`(issue.id)),
                    Update().set(IssueInfo::dirty.name, false),
                    IssueInfo::class.java
                ).awaitSingle()
            } catch (e: SyncNotificator.NotificatedError) {
                TODO("Create IMSIssue")/*
                syncNotificator.sendNotification(
                    issue, SyncNotificator.NotificationDummy(e)
                )
                */
            }
        }
    }
}