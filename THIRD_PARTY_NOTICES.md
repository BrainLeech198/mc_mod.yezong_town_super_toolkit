# Third-party components

This project's All Rights Reserved notice does not replace the licences of its dependencies or build tools.

| Component | Use | Upstream |
|---|---|---|
| Gradle Wrapper | Build bootstrap, Apache-2.0 notices retained in wrapper scripts | https://github.com/gradle/gradle |
| Minecraft / Forge | Forge runtime and development APIs, distributed separately | https://files.minecraftforge.net/ |
| Fabric Loader / Fabric API | Fabric runtime and APIs, distributed separately | https://fabricmc.net/ |
| Forge Config API Port | Fabric configuration dependency, distributed separately | https://github.com/Fuzss/forge-config-api-port |
| Gson / NightConfig | Runtime libraries supplied through the platform dependencies | https://github.com/google/gson / https://github.com/TheElectronWill/night-config |

The final mod packages include this project's own shared classes. They do not include Minecraft, another mod's full installation or server/player data. Dependency declarations are in the Gradle scripts and platform metadata; install the Fabric requirements separately.

Feature comparison with other mods is documented in the roadmap. Such comparison does not mean their source code or complete functionality has been bundled into this project. Any future copied implementation must be reviewed against its original licence and credited separately.
