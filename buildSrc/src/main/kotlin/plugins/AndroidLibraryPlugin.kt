package plugins

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType

class AndroidLibraryPlugin : AndroidPluginBase<LibraryExtension>(LibraryExtension::class) {

    override fun Project.projectSetup() {
        pluginManager.apply("com.android.library")
    }

    override fun LibraryExtension.androidConfig(project: Project) {

    }
}
