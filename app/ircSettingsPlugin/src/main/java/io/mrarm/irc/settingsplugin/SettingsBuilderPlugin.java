package io.mrarm.irc.build;

import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.GradleException;

import java.io.File;

public class SettingsBuilderPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        AppExtension android = project.getExtensions().getByType(AppExtension.class);
        File generatedDirectory = new File(project.getBuildDir(), "generated/source/settings");
        Task generateTask = project.getTasks().create("generateSettings");
        generateTask.doLast(task -> {
            try {
                SettingsBuilder.generateJavaFiles(project.file("settings.yml"), generatedDirectory);
            } catch (Exception e) {
                throw new GradleException("Could not generate settings sources", e);
            }
        });

        android.getApplicationVariants().all(variant ->
                variant.registerJavaGeneratingTask(generateTask, generatedDirectory));
    }
}
