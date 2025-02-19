package eu.glasskube.operator.apps.glitchtip

import eu.glasskube.operator.Labels
import eu.glasskube.operator.generic.dependent.postgres.PostgresNameMapper
import eu.glasskube.operator.generic.dependent.redis.RedisNameMapper
import io.fabric8.kubernetes.api.model.Namespaced
import io.fabric8.kubernetes.client.CustomResource
import io.fabric8.kubernetes.model.annotation.Group
import io.fabric8.kubernetes.model.annotation.Plural
import io.fabric8.kubernetes.model.annotation.Version

@Group("glasskube.eu")
@Version("v1alpha1")
@Plural("glitchtips")
class Glitchtip : CustomResource<GlitchtipSpec, GlitchtipStatus>(), Namespaced {
    companion object {
        const val APP_NAME = "glitchtip"
        const val APP_VERSION = "3.3.0"
        const val UPLOADS_DIR = "/code/uploads"
        const val UPLOADS_VOLUME_NAME = "uploads"
        const val APP_UID = 5000L
    }

    object Redis : RedisNameMapper<Glitchtip>() {
        internal const val NAME = "redis"
        private const val VERSION = "7.0"

        override fun getName(primary: Glitchtip) = "${primary.genericResourceName}-$NAME"

        override fun getVersion(primary: Glitchtip) = VERSION

        override fun getLabels(primary: Glitchtip) =
            Labels.resourceLabels(NAME, primary.metadata.name, Glitchtip.APP_NAME, VERSION, NAME)

        override fun getLabelSelector(primary: Glitchtip) =
            Labels.resourceLabelSelector(NAME, primary.metadata.name, Glitchtip.APP_NAME)
    }

    object Postgres : PostgresNameMapper<Glitchtip>() {
        override fun getName(primary: Glitchtip) = "${primary.genericResourceName}-db"
        override fun getLabels(primary: Glitchtip) = primary.resourceLabels
        override fun getDatabaseName(primary: Glitchtip) = "glitchtip"
    }
}

val Glitchtip.resourceLabels
    get() = Labels.resourceLabels(Glitchtip.APP_NAME, metadata.name, Glitchtip.APP_NAME, Glitchtip.APP_VERSION)
val Glitchtip.resourceLabelSelector
    get() = Labels.resourceLabelSelector(Glitchtip.APP_NAME, metadata.name, Glitchtip.APP_NAME)
val Glitchtip.genericResourceName get() = "${Glitchtip.APP_NAME}-${metadata.name}"
val Glitchtip.workerName get() = "$genericResourceName-worker"
val Glitchtip.secretName get() = genericResourceName
val Glitchtip.configMapName get() = genericResourceName
val Glitchtip.ingressName get() = genericResourceName
val Glitchtip.httpServiceName get() = "$genericResourceName-http"
val Glitchtip.ingressTlsCertName get() = "$genericResourceName-cert"
