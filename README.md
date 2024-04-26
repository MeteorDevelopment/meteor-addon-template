# Meteor Addon Template

A template to allow easy usage of the Meteor Addon API.

### How to use

- Clone this project
- Use this template to create new modules/commands
- Build the executable using the gradle `build` task.
- Run the mod with Meteor.

### Project structure

```text
.
│── .github
│   ╰── workflows
│       │── dev_build.yml
│       ╰── pull_request.yml
│── gradle
│   ╰── wrapper
│       │── gradle-wrapper.jar
│       ╰── gradle-wrapper.properties
│── src
│   ╰── main
│       │── java
│       │   ╰── com
│       │       ╰── example
│       │           ╰── addon
│       │               │── commands
│       │               │   ╰── CommandExample
│       │               │── hud
│       │               │   ╰── HudExample
│       │               │── modules
│       │               │   ╰── ModuleExample
│       │               ╰── AddonTemplate
│       ╰── resources
│           │── assets
│           │   ╰── template
│           │       ╰── icon.png
│           │── addon-template.mixins.json
│           ╰── fabric.mod.json
│── .editorconfig
│── .gitignore
│── build.gradle
│── gradle.properties
│── gradlew
│── gradlew.bat
│── LICENSE
│── README.md
╰── settings.gradle
```

This is the default project structure. Each folder/file has a specific purpose.  
Here is a brief explanation of the ones you might need to modify:

- `.github/workflows`: Contains the GitHub Actions configuration files.
- `gradle`: Contains the Gradle wrapper files.  
  Edit the `gradle.properties` file to change the version of the Gradle wrapper.
- `src/main/java/com/example/addon`: Contains the main class of the addon.  
  Here you can register your custom commands, modules, and HUDs.  
  Edit the `getPackage` method to reflect the package of your addon.
- `src/main/resources`: Contains the resources of the addon.
    - `assets`: Contains the assets of the addon.  
      You can add your own assets here, separated in subfolders.
        - `template`: Contains the assets of the template.  
          You can replace the `icon.png` file with your own addon icon.  
          Also, rename this folder to reflect the name of your addon.
    - `addon-template.mixins.json`: Contains the Mixin configuration for the addon.  
      You can add your own mixins in the `client` array.
    - `fabric.mod.json`: Contains the metadata of the addon.  
      Edit the various fields to reflect the metadata of your addon.
- `build.gradle`: Contains the Gradle build script.  
  You can manage the dependencies of the addon here.  
  Remember to keep the `fabric-loom` version up-to-date.
- `gradle.properties`: Contains the properties of the Gradle build.  
  These will be used by the build script.
- `LICENSE`: Contains the license of the addon.  
  You can edit this file to change the license of your addon.
- `README.md`: Contains the documentation of the addon.  
  You can edit this file to reflect the documentation of your addon, and showcase its features.
