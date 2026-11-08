package io.mrarm.irc.build;

import com.android.build.api.variant.ApplicationAndroidComponentsExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * Contributes the generated settings classes to each Android variant using the
 * public Android Components API. No legacy applicationVariants hooks are needed.
 */
public class SettingsBuilderPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        ApplicationAndroidComponentsExtension androidComponents =
                project.getExtensions().getByType(ApplicationAndroidComponentsExtension.class);

        androidComponents.onVariants(androidComponents.selector().all(), variant -> {
            String name = variant.getName();
            String taskName = "generate" + Character.toUpperCase(name.charAt(0))
                    + name.substring(1) + "Settings";

            TaskProvider<GenerateSettingsTask> generation =
                    project.getTasks().register(taskName, GenerateSettingsTask.class, task -> {
                        task.getSettingsFile().set(
                                project.getLayout().getProjectDirectory().file("settings.yml"));
                        // Variant isolation prevents parallel APK builds sharing generated outputs.
                        task.getOutputDirectory().set(project.getLayout().getBuildDirectory()
                                .dir("generated/source/settings/" + name));
                    });

            variant.getSources().getJava().addGeneratedSourceDirectory(
                    generation, GenerateSettingsTask::getOutputDirectory);
        });
    }
}
