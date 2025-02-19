package eu.glasskube.operator.apps.nextcloud

import eu.glasskube.kubernetes.client.patchOrUpdateStatus
import eu.glasskube.operator.Labels
import eu.glasskube.operator.api.reconciler.getSecondaryResource
import eu.glasskube.operator.api.reconciler.informerEventSource
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudConfigMap
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudCronJob
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudDeployment
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudIngress
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudMinioBucket
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudOfficeDeployment
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudOfficeService
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudPostgresBackup
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudPostgresCluster
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudRedisDeployment
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudRedisService
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudService
import eu.glasskube.operator.apps.nextcloud.dependent.NextcloudVolume
import eu.glasskube.operator.generic.condition.isReady
import eu.glasskube.operator.infra.postgres.PostgresCluster
import eu.glasskube.operator.infra.postgres.isReady
import eu.glasskube.operator.logger
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.javaoperatorsdk.operator.api.reconciler.Context
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer
import io.javaoperatorsdk.operator.api.reconciler.Reconciler
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent
import kotlin.jvm.optionals.getOrDefault

@ControllerConfiguration(
    dependents = [
        Dependent(type = NextcloudConfigMap::class, name = "NextcloudConfigMap"),
        Dependent(type = NextcloudVolume::class, name = "NextcloudVolume"),
        Dependent(type = NextcloudIngress::class, name = "NextcloudIngress"),
        Dependent(type = NextcloudMinioBucket::class, name = "NextcloudMinioBucket"),
        Dependent(
            type = NextcloudService::class,
            name = "NextcloudService",
            useEventSourceWithName = NextcloudReconciler.SERVICE_EVENT_SOURCE
        ),
        Dependent(
            type = NextcloudPostgresCluster::class,
            name = "NextcloudPostgresCluster",
            readyPostcondition = NextcloudPostgresCluster.ReadyPostCondition::class,
            dependsOn = ["NextcloudMinioBucket"]
        ),
        Dependent(
            type = NextcloudPostgresBackup::class,
            name = "NextcloudPostgresBackup",
            dependsOn = ["NextcloudPostgresCluster"]
        ),
        Dependent(
            type = NextcloudDeployment::class,
            name = "NextcloudDeployment",
            readyPostcondition = NextcloudDeployment.ReadyPostCondition::class,
            useEventSourceWithName = NextcloudReconciler.DEPLOYMENT_EVENT_SOURCE,
            dependsOn = ["NextcloudVolume", "NextcloudConfigMap", "NextcloudPostgresCluster"]
        ),
        Dependent(type = NextcloudCronJob::class, name = "NextcloudCronJob", dependsOn = ["NextcloudDeployment"]),
        Dependent(
            type = NextcloudRedisDeployment::class,
            name = "NextcloudRedisDeployment",
            useEventSourceWithName = NextcloudReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = NextcloudRedisService::class,
            name = "NextcloudRedisService",
            useEventSourceWithName = NextcloudReconciler.SERVICE_EVENT_SOURCE
        ),
        Dependent(
            type = NextcloudOfficeDeployment::class,
            name = "NextcloudOfficeDeployment",
            reconcilePrecondition = NextcloudOfficeDeployment.ReconcilePrecondition::class,
            useEventSourceWithName = NextcloudReconciler.DEPLOYMENT_EVENT_SOURCE
        ),
        Dependent(
            type = NextcloudOfficeService::class,
            name = "NextcloudOfficeService",
            reconcilePrecondition = NextcloudOfficeService.ReconcilePrecondition::class,
            useEventSourceWithName = NextcloudReconciler.SERVICE_EVENT_SOURCE
        )
    ]
)
class NextcloudReconciler : Reconciler<Nextcloud>, EventSourceInitializer<Nextcloud> {
    override fun reconcile(resource: Nextcloud, context: Context<Nextcloud>) = with(context) {
        log.info("Reconciling ${resource.metadata.name}@${resource.metadata.namespace}")
        resource.patchOrUpdateStatus(
            NextcloudStatus(
                readyReplicas = getSecondaryResource(NextcloudDeployment.Discriminator())
                    .map { it.status?.readyReplicas ?: 0 }.getOrDefault(0),
                redisReady = getSecondaryResource(NextcloudRedisDeployment.Discriminator())
                    .map { it.isReady }.getOrDefault(false),
                postgresReady = getSecondaryResource<PostgresCluster>()
                    .map { it.isReady }.getOrDefault(false),
                officeReady = getSecondaryResource(NextcloudOfficeDeployment.Discriminator())
                    .map { it.isReady }.getOrDefault(false)
            )
        )
    }

    override fun prepareEventSources(context: EventSourceContext<Nextcloud>) = with(context) {
        mutableMapOf(
            DEPLOYMENT_EVENT_SOURCE to informerEventSource<Deployment>(),
            SERVICE_EVENT_SOURCE to informerEventSource<Service>()
        )
    }

    companion object {
        const val SELECTOR =
            "${Labels.MANAGED_BY_GLASSKUBE},${Labels.PART_OF}=${Nextcloud.APP_NAME},${Labels.NAME}=${Nextcloud.APP_NAME}"
        const val OFFICE_SELECTOR =
            "${Labels.MANAGED_BY_GLASSKUBE},${Labels.PART_OF}=${Nextcloud.APP_NAME},${Labels.NAME}=${Nextcloud.OFFICE_NAME}"
        const val REDIS_SELECTOR =
            "${Labels.MANAGED_BY_GLASSKUBE},${Labels.PART_OF}=${Nextcloud.APP_NAME},${Labels.NAME}=${Nextcloud.Redis.NAME}"

        internal const val SERVICE_EVENT_SOURCE = "NextcloudServiceEventSource"
        internal const val DEPLOYMENT_EVENT_SOURCE = "NextcloudDeploymentEventSource"

        private val log = logger()
    }
}
