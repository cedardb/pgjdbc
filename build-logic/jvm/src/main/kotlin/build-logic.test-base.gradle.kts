import com.github.vlsi.gradle.dsl.configureEach
import com.github.vlsi.gradle.properties.dsl.props
import org.gradle.api.tasks.testing.Test

plugins {
    id("java-library")
    id("build-logic.build-params")
}

tasks.configureEach<Test> {
    buildParameters.testJdk?.let {
        javaLauncher.convention(javaToolchains.launcherFor(it))
    }
    inputs.file("../build.properties")
    if (file("../build.local.properties").exists()) {
        inputs.file("../build.local.properties")
    }
    inputs.file("../ssltest.properties")
    if (file("../ssltest.local.properties").exists()) {
        inputs.file("../ssltest.local.properties")
    }
    testLogging {
        showStandardStreams = true
    }
    exclude("**/*Suite*")

    // CedarDB-specific excludes with justification

    // We only support UTF8 encoding
    exclude("**/*ClientEncodingTest*")


    // We don't support replication yet
    exclude("**/*replication*")

    // Takes too long, should fix
    exclude("**/*BatchedInsertReWriteEnabled*")
    exclude("**/*LargeObjectManager*")

    // We don't support the money type
    exclude("**/*JBuilderTest*")

    // We don't support scram
    exclude("**/*ScramTest*")
    exclude("**/*AuthenticationPluginTest*")
    exclude("**/*PasswordUtilTest*")

    // Missing DROP SCHEMA
    exclude("**/*OptionsProperty*")
    exclude("**/*SchemaTest*")

    exclude("**/*SearchPathLookupTest*")
    exclude("**/jdbc4/DatabaseMetaDataTest*")
    exclude("**/jdbc42/DatabaseMetaDataTest*")

    // Missing lo_creat
    exclude("**/*BlobTest*")
    exclude("**/*BlobTransactionTest*")
    exclude("**/*Jdbc3BlobTest*")
    exclude("**/*LargeObjectManagerTest*")

    // We don't support date infinity literals
    exclude("**/*GetObject310InfinityTests*")
    exclude("**/*SetObject310InfinityTests*")

    // We don't support custom types
    exclude("**/*CustomTypeWithBinaryTransferTest*")
    exclude("**/*ServerErrorTest*")
    exclude("**/*StringTypeParameterTest*")
    exclude("**/*DatabaseMetaDataTest*")
    exclude("**/*CompositeTest*")
    exclude("**/jdbc3/DatabaseMetaDataTest*")
    exclude("**/*DatabaseMetaDataHideUnprivilegedObjectsTest*")

    // We don't implement all geometric types
    exclude("**/*GeometricTest*")
    exclude("**/*PGObjectGetTest*")
    exclude("**/*PGObjectSetTest*")

    // We don't support enums
    exclude("**/*EnumTest*")

    // We don't support listen/notify
    exclude("**/*NotifyTest*")

    // We don't support the xml type
    exclude("**/*PgSQLXMLTest*")

    // We don't support restoring save points
    exclude("**/*Jdbc3SavepointTest*")

    // We don't support pg_sleep
    exclude("**/*SocketTimeoutTest*")

    // We don't support plpgsql
    exclude("**/*Jdbc42CallableStatementTest*")
    exclude("**/*Jdbc3CallableStatementTest*")
    exclude("**/*EscapeSyntaxCallModeCallIfNoReturnTest*")
    exclude("**/*EscapeSyntaxCallModeCallTest*")
    exclude("**/*EscapeSyntaxCallModeSelectTest*")
    exclude("**/*ProcedureTransactionTest*")
    exclude("**/*TypesTest*")
    exclude("**/*CallableStmtTest*")
    exclude("**/*RefCursorTest*")
    exclude("**/*RefCursorFetchTest*")

    // We don't support binary output for arrays
    exclude("**/*ArrayTest*")

    // We don't support all the log properties and levels
    exclude("**/*LogServerMessagePropertyTest*")

    // We don't support row expressions
    exclude("**/*NoColumnMetadataIssue1613Test*")

    // We don't support server side cursors
    exclude("**/*ServerCursorTest*")
    exclude("**/*AdaptiveFetchSizeTest*")

    // We don't support alter database
    exclude("**/*DatabaseMetaDataTransactionIsolationTest*")

    jvmArgs("-Xmx1536m")
    jvmArgs("-Djdk.net.URLClassPath.disableClassPathURLCheck=true")
    props.string("testExtraJvmArgs").trim().takeIf { it.isNotBlank() }?.let {
        jvmArgs(it.split(" ::: "))
    }
    // Pass the property to tests
    fun passProperty(name: String, default: String? = null) {
        val value = System.getProperty(name) ?: default
        value?.let { systemProperty(name, it) }
    }
    passProperty("preferQueryMode")
    passProperty("java.awt.headless")
    passProperty("user.language", "TR")
    passProperty("user.country", "tr")
    val props = System.getProperties()
    @Suppress("UNCHECKED_CAST")
    for (e in props.propertyNames() as `java.util`.Enumeration<String>) {
        if (e.startsWith("pgjdbc.")) {
            passProperty(e)
        }
    }
    for (p in listOf("server", "port", "database", "username", "password",
        "privilegedUser", "privilegedPassword",
        "simpleProtocolOnly", "enable_ssl_tests")) {
        passProperty(p)
    }
}
