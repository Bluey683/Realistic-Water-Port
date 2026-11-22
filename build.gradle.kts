import java.util.regex.Pattern

plugins {
	id("net.fabricmc.fabric-loom")
	`maven-publish`
}

repositories {
	maven {
		name = "Modrinth"
		url = uri("https://api.modrinth.com/maven")
	}
}

loom {
	splitEnvironmentSourceSets()

	mods {
		register("realistic-water") {
			sourceSet(sourceSets.main.get())
			sourceSet(sourceSets.getByName("client"))
		}
	}
}

// Minecraft 26.2 uses the Blaze3D package names. The shared client source
// was authored against the 26.3 RenderPearl names, so remap those names
// before javac sees the source. The mapping also covers slash-form mixin
// descriptors embedded in Java strings.
val remappedClientDir = layout.buildDirectory.dir("generated/sources/mc26.2/client")
val remapClient262 by tasks.registering(Sync::class) {
	val renameFile = layout.projectDirectory.file("versions/mc26.2/renames.txt")
	inputs.file(renameFile)
	from("src/client/java") {
		include("**/*.java")
		filter { line: String ->
			val pairs = renameFile.asFile.readLines()
				.mapNotNull { raw ->
					val row = raw.substringBefore("#").trim()
					if (row.isEmpty()) null else row.split(Regex("\\s+")).takeIf { it.size == 2 }
				}
				.flatMap { pair ->
					listOf(
						pair[0] to pair[1],
						pair[0].replace('.', '/') to pair[1].replace('.', '/')
					)
				}
				.sortedByDescending { it.first.length }
			var result = line
			for ((from, to) in pairs) {
				result = result.replace(Regex(Pattern.quote(from) + "(?![A-Za-z0-9_])"), to)
			}
			result
		}
	}
	into(remappedClientDir)
}

sourceSets.getByName("client").java.setSrcDirs(listOf(remappedClientDir))
tasks.named("compileClientJava") {
	dependsOn(remapClient262)
}

dependencies {
	minecraft("com.mojang:minecraft:${providers.gradleProperty("minecraft_version").get()}")
	implementation("net.fabricmc:fabric-loader:${providers.gradleProperty("loader_version").get()}")
	implementation("net.fabricmc.fabric-api:fabric-api:${providers.gradleProperty("fabric_api_version").get()}")

	// Compile against the exact 26.2 builds; the checked-in 26.3 jars are
	// deliberately not used because their package names do not exist in 26.2.
	"clientCompileOnly"("maven.modrinth:AANobbMI:xJZxADzI")
	"clientCompileOnly"("maven.modrinth:oSIKhgz3:jmyfnI6L")
}

tasks.processResources {
	val version = version
	inputs.property("version", version)

	filesMatching("fabric.mod.json") {
		expand("version" to version)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_${projectName}" }
	}
}

tasks.named<Jar>("sourcesJar") {
	val projectName = project.name
	inputs.property("projectName", projectName)

	from("LICENSE") {
		rename { "${it}_${projectName}" }
	}
}

publishing {
	publications {
		register<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}

	repositories {
	}
}
