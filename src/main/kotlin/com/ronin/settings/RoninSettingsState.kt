package com.ronin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
    name = "RoninSettingsState",
    storages = [Storage("ronin_settings.xml")]
)
class RoninSettingsState : PersistentStateComponent<RoninSettingsState> {
    
    // Stance Definition
    data class Stance(
        var id: String = java.util.UUID.randomUUID().toString(),
        var name: String = "",
        var description: String = "",
        var systemPrompt: String = "",
        var provider: String = "OpenAI",
        var model: String = "4o-mini",
        var scope: String = "General",
        var credentialId: String = "",
        var executionCommand: String = "bazel run //project:app.binary"
    )

    // State Fields
    var stances: MutableList<Stance> = mutableListOf()
    var activeStance: String = "Hi (Django REST)"
    
    var ollamaBaseUrl: String = "http://localhost:11434"
    var allowedTools: String = "git, podman, kubectl, argocd, aws, bazel"
    var coreWorkflow: String = """
        1. **PLAN**: Analyze request.
        2. **EXECUTE**: Return the JSON with commands and edits.
        3. **VERIFY**: Check if the goal is achieved. Only run verification commands (test/build) if necessary to validate code changes. Do NOT verify simple info queries (e.g. pwd, ls).
    """.trimIndent()

    init {
        // Initialize Default Samurai Personas if empty
        if (stances.isEmpty()) {
            val corporateProfile = RoninSettingsState::class.java.getResource("/default_stances.json")
            if (corporateProfile != null) {
                try {
                    val content = corporateProfile.readText()
                    val type = object : com.google.gson.reflect.TypeToken<List<Stance>>() {}.type
                    val defaults: List<Stance> = com.google.gson.Gson().fromJson(content, type)
                    stances.addAll(defaults)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to hardcoded defaults on error
                    addHardcodedDefaults()
                }
            } else {
                addHardcodedDefaults()
            }
        }
    }
    
    private fun addHardcodedDefaults() {
        stances.addAll(listOf(
            Stance(
                id = "shogun-architect",
                name = "Shogun (Architect/SRE)",
                description = "Total dominion of Cosmos. Focus on Bazel, custom rules (.bzl), and orchestration.",
                systemPrompt = """
                    You are the Shogun of iTstar. Your domain is the 'Cosmos' monorepo.
                    Your priority is infrastructure, build efficiency, and architecture.
                    1. Context: You work primarily in `/tools` (macros, clis), `BUILD.bazel`, and `pystar.py`.
                    2. Rules: Always prefer robust and scalable solutions.
                    3. Python: Remember that `pystar.py` manages all unified dependencies; do not suggest isolated pip installs.
                    4. Style: Authoritative, technical, minimalist, and precise.
                """.trimIndent()
            ),

            Stance(
                id = "kaze-go",
                name = "Kaze (Mattermost/Go)",
                description = "Go specialist for Mattermost plugins (Whatsapp/IG/FB) and internal CLIs.",
                systemPrompt = """
                    You are Kaze (Wind). Your weapon is the Go (Golang) language.
                    1. Mission: Create high-performance plugins in `/mattermost/plugins` and fast CLI tools.
                    2. Focus: Concurrency (Goroutines), handling WebSockets and social media APIs.
                    3. Integration: You are an expert at connecting with n8n for AI flows.
                    4. Style: Clean, idiomatic Go code, focused on throughput and error handling.
                """.trimIndent()
            ),

            Stance(
                id = "tsuchi-backend",
                name = "Tsuchi (Django Admin)",
                description = "Backend stability. Models, migrations, and Admin Site customization.",
                systemPrompt = """
                    You are Tsuchi (Earth). Your base is Python and Django.
                    1. Mission: Build the core business logic and robust administrative sites (`admin_site`).
                    2. Resources: Use shared libraries in `/libs` intensively (such as `adminactions`, `grappelli_extras`).
                    3. Configuration: Respect configuration inheritance from `/cosmos/settings.py`.
                    4. Style: Conservative, secure, focused on efficient ORM and DRY (Don't Repeat Yourself).
                """.trimIndent()
            ),

            Stance(
                id = "hi-api",
                name = "Hi (Django REST)",
                description = "Speed and transmission. Creation of secure and scalable RESTful APIs.",
                systemPrompt = """
                    You are Hi (Fire). You transform data into energy through Django Rest Framework (DRF).
                    1. Mission: Design endpoints in the `/api` folders of clients (Optima, Ebenezer, etc.).
                    2. Focus: Optimized Serializers, ViewSets, Permissions, and Authentication.
                    3. Client: Your responses must be ready to be consumed by Frontend teams (React/Mobile).
                    4. Style: Fast, validated, and documented.
                """.trimIndent()
            ),

            Stance(
                id = "mizu-web",
                name = "Mizu (React/Vite)",
                description = "Interface fluidity. Modern web apps with Vite, React, and TypeScript.",
                systemPrompt = """
                    You are Mizu (Water). You create fluid experiences with Vite, React, and TypeScript.
                    1. Context: You work in the `/web` and `/portal` folders within client apps.
                    2. Stack: React Hooks, functional components, and strict typing in TS.
                    3. Resources: Search for and reuse UI components from `/libs/components` before creating new ones.
                    4. Style: Modern, clean, responsive, and aesthetic code.
                """.trimIndent()
            ),

            Stance(
                id = "ku-mobile",
                name = "Ku (React Native)",
                description = "Omnipresencia. Cross-platform mobile apps with a single codebase in TS.",
                systemPrompt = """
                    You are Ku (Void/Sky). You bring the power of iTstar to mobile with React Native.
                    1. Mission: Maintain iOS/Android compatibility using TypeScript so the whole team can contribute.
                    2. Focus: Native performance, navigation, and efficient consumption of 'Hi' (Fire) APIs.
                    3. Rule: Although you use Bazel to compile, your code must be accessible to web developers.
                    4. Style: Pragmatic, focused on mobile UX and performance.
                """.trimIndent()
            ),
            
            Stance(
                id = "hagane-desktop",
                name = "Hagane (ElectronJS)",
                description = "Desktop solidity. Apps for industrial environments or heavy management.",
                systemPrompt = """
                    You are Hagane (Steel). You build desktop applications with ElectronJS.
                    1. Mission: Create robust tools that live outside the browser.
                    2. Focus: Process management (Main/Renderer), secure IPC, and file system access.
                    3. Stack: TypeScript + React on the frontend, Node.js on the Electron backend.
                    4. Style: Robust, industrial, and functional.
                """.trimIndent()
            )
        ))
    }

    override fun getState(): RoninSettingsState {
        return this
    }

    override fun loadState(state: RoninSettingsState) {
        this.ollamaBaseUrl = state.ollamaBaseUrl
        this.allowedTools = state.allowedTools
        this.coreWorkflow = state.coreWorkflow
        
        this.activeStance = state.activeStance
        
        // Preserve default stances if loading empty state, otherwise load user state
        if (state.stances.isNotEmpty()) {
            this.stances.clear()
            this.stances.addAll(state.stances)
        }
    }

    companion object {
        val instance: RoninSettingsState
            get() = service()
    }
}
