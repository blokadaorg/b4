package org.blokada.b4

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import buildtype.newBuildTypeModule
import com.github.salomonbrys.kodein.Kodein
import com.github.salomonbrys.kodein.KodeinAware
import com.github.salomonbrys.kodein.instance
import com.github.salomonbrys.kodein.lazy
import core.*
import flavor.newFlavorModule
import gs.environment.Journal
import gs.environment.inject
import gs.environment.newGscoreModule
import gs.property.newDeviceModule
import gs.property.newUserModule
import io.flutter.app.FlutterApplication
import org.acra.ACRA
import org.acra.ReportField
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.DialogConfigurationBuilder
import org.acra.config.HttpSenderConfigurationBuilder
import org.acra.config.LimiterConfigurationBuilder
import org.acra.data.StringFormat
import org.acra.sender.HttpSender
import org.blokada.BuildConfig
import org.blokada.R


/**
 * TODO: this is a copy from legacy with different parent class. Make it nicer.
 */

class MainApplication: FlutterApplication(), KodeinAware {

    override val kodein by Kodein.lazy {
        import(newGscoreModule(this@MainApplication))
        import(newDeviceModule(this@MainApplication))
        import(newUserModule(this@MainApplication))
        import(newTunnelModule(this@MainApplication))
        import(newFiltersModule(this@MainApplication))
        import(newDnsModule(this@MainApplication))
        import(newWelcomeModule(this@MainApplication))
        import(newPagesModule(this@MainApplication))
        import(newUpdateModule(this@MainApplication))
        import(newKeepAliveModule(this@MainApplication))
        import(newAppModule(this@MainApplication), allowOverride = true)
        import(newFlavorModule(this@MainApplication), allowOverride = true)
        import(newBuildTypeModule(this@MainApplication), allowOverride = true)
    }

    override fun onCreate() {
        super.onCreate()
        setRestartAppOnCrash()
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        val builder = CoreConfigurationBuilder(this)
        builder.setBuildConfigClass(BuildConfig::class.java).setReportFormat(StringFormat.JSON)
        builder.setLogcatArguments("-t", "300", "-v", "threadtime")
        builder.setReportContent(
                ReportField.INSTALLATION_ID,
                ReportField.USER_COMMENT,
                ReportField.BUILD_CONFIG,
                ReportField.CUSTOM_DATA,
                ReportField.SHARED_PREFERENCES,
                ReportField.STACK_TRACE,
                ReportField.THREAD_DETAILS,
                ReportField.LOGCAT,
                ReportField.PACKAGE_NAME,
                ReportField.ANDROID_VERSION,
                ReportField.APP_VERSION_NAME,
                ReportField.TOTAL_MEM_SIZE,
                ReportField.AVAILABLE_MEM_SIZE,
                ReportField.SETTINGS_SYSTEM,
                ReportField.SETTINGS_SECURE
        )
        builder.setAdditionalSharedPreferences("default", "basic")

        val c = builder.getPluginConfigurationBuilder(HttpSenderConfigurationBuilder::class.java)
        c.setEnabled(true)
        c.setHttpMethod(HttpSender.Method.POST)
        c.setUri(getString(R.string.acra_uri))
        c.setBasicAuthLogin(getString(R.string.acra_login))
        c.setBasicAuthPassword(getString(R.string.acra_password))
        c.setDropReportsOnTimeout(true)

        if (ProductType.isPublic()) {
            val limiter = builder.getPluginConfigurationBuilder(LimiterConfigurationBuilder::class.java)
            limiter.setEnabled(true)
            limiter.setOverallLimit(10)
            limiter.setStacktraceLimit(3)
            limiter.setExceptionClassLimit(5)
            limiter.setResIgnoredCrashToast(R.string.main_report_limit)
        }

        val dialog = builder.getPluginConfigurationBuilder(DialogConfigurationBuilder::class.java)
        dialog.setEnabled(true)
        dialog.setResTitle(R.string.main_report_title)
        dialog.setResText(R.string.main_report_text)
        dialog.setResCommentPrompt(R.string.main_report_comment)
        dialog.setResTheme(R.style.GsTheme_Dialog)
        dialog.setResIcon(R.drawable.ic_blokada)
        ACRA.init(this, builder)
    }

    private fun setRestartAppOnCrash() {
        Thread.setDefaultUncaughtExceptionHandler { _, ex ->
            try {
                ACRA.getErrorReporter().handleException(ex)
                val j: Journal = inject().instance()
                j.log("fatal", ex)
            } catch (e: Exception) {}
            startThroughJobScheduler(this)
            System.exit(2)
        }
    }
}

private fun startThroughJobScheduler(
        ctx: Context,
        scheduler: JobScheduler = ctx.inject().instance()
) {
    val serviceComponent = ComponentName(ctx, BootJobService::class.java)
    val builder = JobInfo.Builder(0, serviceComponent)
    builder.setOverrideDeadline(3 * 1000L)
    scheduler.schedule(builder.build())
}
