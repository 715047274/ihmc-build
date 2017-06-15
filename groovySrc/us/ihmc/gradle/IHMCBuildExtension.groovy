package us.ihmc.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * <p>
 * The {@code IHMCBuildExtension} provides some helper extensions to any project
 * that it is applied to exposed via the {@code ihmc} property on the project. Much
 * of this is specific to the way in which we organize our code and dependencies in
 * the IHMC Robotics Lab so may be of dubious usefulness to others.
 * </p>
 *
 * @author Doug Stephen <a href="mailto:dstephen@ihmc.us">(dstephen@ihmc.us)</a>
 */
class IHMCBuildExtension
{
   private final Project containingProject;

   def IHMCBuildExtension(Project project)
   {
      this.containingProject = project
   }

   /**
    * <p>
    * Set up a closure containing all of the commonly used artifact repos and
    * proxies common across IHMC Robotics software.
    * </p>
    *
    * @return a {@link Closure that generates all of the "default" {@link org.gradle.api.artifacts.repositories.ArtifactRepository}s we typically use
    */
   def Closure ihmcDefaultArtifactProxies()
   {
      return {
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-vendor"
         }
         maven {
            url "http://dl.bintray.com/ihmcrobotics/maven-release"
         }
         maven {
            url "http://clojars.org/repo/"
         }
         maven {
            url "https://github.com/rosjava/rosjava_mvn_repo/raw/master"
         }
         maven {
            url "https://oss.sonatype.org/content/repositories/snapshots"
         }
         maven {
            url "https://artifactory.ihmc.us/artifactory/releases/"
         }
         maven {
            url "https://artifactory.ihmc.us/artifactory/thirdparty/"
         }
         maven {
            url "http://artifactory.ihmc.us/artifactory/snapshots/"
         }
      }
   }

   def String getSnapshotVersion(String version, buildNumber)
   {
      return version + "-SNAPSHOT-" + buildNumber;
   }

   def String getNightlyVersion(String version)
   {
      return version + "-NIGHTLY-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
   }

   def void setupSourceSetStructure()
   {
      setupJavaSourceSets(containingProject)

      if (project.plugins.hasPlugin(GroovyPlugin))
      {
         setupGroovySourceSets(containingProject)
      }
   }

   private void setupGroovySourceSets(Project project)
   {
      project.sourceSets {
         main {
            groovy {
               srcDirs = ['groovySrc']
            }

            resources {
               srcDirs = ['src', 'resources']
            }
         }

         test {
            groovy {
               srcDirs = ['groovyTest']
            }

            resources {
               srcDirs = ['testResources']
            }
         }
      }
   }

   private void setupJavaSourceSets(Project project)
   {
      project.sourceSets {
         main {
            java {
               srcDirs = ['src']
            }

            resources {
               srcDirs = ['src', 'resources']
            }
         }

         test {
            java {
               srcDirs = ['test']
            }

            resources {
               srcDirs = ['testResources']
            }
         }
      }
   }

   /**
    * <p>
    * This method is used to naïvely look up a Project in a multi-project build by its name,
    * irrespective of the hierarchy that contains it. The primary use case for this is to allow for
    * configuration-independent lookup of local source depenencies that can either be part of a standalone
    * multi-project build or as part of a several-level nested developer workspace. E.g., source dependencies
    * in the project IHMCOpenRoboticsSoftware can be identified as ":ProjectName" if being built within IHMCOpenRoboticsSoftware,
    * or as ":IHMCOpenRoboticsSoftware:ProjectName" if the build is kicked off by a developer workspace. Example usage of this method
    * would be:
    * </p>
    * <pre>
    * {@code
    * dependencies{
    *   compile ihmc.getProjectDependency(":ProjectName")
    *}
    * }
    * </pre>
    *
    * <p>
    * The lookup is very simple in that it starts at the {@link Project#getRootProject()} property and gets a flattened list of all
    * child projects and then returns the first successful result of {@link String#endsWith(java.lang.String)} by comparing with {@link Project#getPath()}.
    * If there are no successful matches, it will return {@code null}.
    * </p>
    *
    * @param projectName The project name to locate
    * @return The {@link Project} matching the name based on the rules described above, or {@code null} if there is no match.
    */
   def Project getProjectDependency(String projectName)
   {
      def projects = getAllProjects(containingProject.getRootProject())

      for (Project project : projects)
      {
         if (project.path.endsWith(projectName))
         {
            containingProject.logger.debug("IHMC Build Extension Found ${project.path} for getProjectDependency(${projectName})")
            return project
         }
      }

      throw new GradleException("Could not find project with name ${projectName}.\nPlease make sure that there is a directory with the given name and that it " +
            "contains a 'build.gradle' file somewhere in your workspace.")
   }

   /**
    * <p>
    * Uses the same search mechanism as {@link IHMCBuildExtension#getProjectDependency(java.lang.String)} but returns the
    * fully qualified Gradle path to the project as a String instead of the project object.
    * </p>
    *
    * @param projectName The project name to locate
    * @return The fully qualified Gradle path to the project as given by {@link Project#getPath()}
    */
   def String getProjectDependencyGradlePath(String projectName)
   {
      return getProjectDependency(projectName).path
   }

   def DefaultProjectDependency getProjectTestDependency(String projectName)
   {
      String depPath = getProjectDependencyGradlePath(projectName)

      return containingProject.dependencies.project(path: "${depPath}", configuration: 'testOutput')
   }

   def void configureForIHMCOpenSourceBintrayPublish(boolean isDryRun, String mavenPublicationName, String bintrayRepoName, List<String> packageLabels)
   {
      containingProject.configure(containingProject) { projectToConfigure ->
         apply plugin: 'com.jfrog.bintray'

         bintray {
            user = projectToConfigure.hasProperty("bintray_user") ? projectToConfigure.bintray_user : "invalid"
            key = projectToConfigure.hasProperty("bintray_key") ? projectToConfigure.bintray_key : "invalid"

            if (user.equals("invalid"))
            {
               projectToConfigure.logger.debug("Bintray user name property not set. Please set the 'bintray_user' property in ~/.gradle/gradle.properties. See https://github.com/bintray/gradle-bintray-plugin")
            }

            if (key.equals("invalid"))
            {
               projectToConfigure.logger.debug("Bintray API key property not set. Please set the 'bintray_key' property in ~/.gradle/gradle.properties. See https://github.com/bintray/gradle-bintray-plugin")
            }

            dryRun = isDryRun
            publish = false

            publications = [mavenPublicationName]

            pkg {
               repo = bintrayRepoName
               name = projectToConfigure.name
               userOrg = 'ihmcrobotics'
               desc = "IHMC Open Robotics Software Project ${projectToConfigure.name}"

               websiteUrl = projectToConfigure.ext.vcsUrl
               issueTrackerUrl = "${projectToConfigure.ext.vcsUrl}/issues"
               vcsUrl = "${projectToConfigure.ext.vcsUrl}.git"

               licenses = [projectToConfigure.ext.bintrayLicenseName]
               labels = packageLabels
               publicDownloadNumbers = true

               version {
                  name = projectToConfigure.ext.fullVersion
                  desc = "IHMC Open Robotics Software Project ${projectToConfigure.name} v${projectToConfigure.ext.fullVersion}"
                  released = new Date()
                  vcsTag = "v${projectToConfigure.version}"
               }
            }
         }
      }
   }

   def void devCheck()
   {
      println containingProject.name
   }

   private List<Project> getAllProjects(Project rootProject)
   {
      def ret = new ArrayList<Project>()
      ret.add(rootProject)

      if (!rootProject.childProjects.isEmpty())
      {
         getAllProjectsFlattened(rootProject.childProjects.values(), ret)
      }

      return ret
   }

   private void getAllProjectsFlattened(Collection<Project> projects, List<Project> flatProjects)
   {
      for (Project project : projects)
      {
         if (!project.childProjects.isEmpty())
         {
            getAllProjectsFlattened(project.childProjects.values(), flatProjects)
         } else
         {
            flatProjects.add(project)
         }
      }
   }
}
