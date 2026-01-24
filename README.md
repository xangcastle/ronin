# ⚔️ Ronin

> **"The masterless coding agent. Serving only your code."**


<!-- Plugin description -->
**Ronin** is the masterless coding agent, serving only your code. 


![Ronin.png](docs/Ronin.png)

## ⚡ The Philosophy: Stability in a Volatile World

**Ronin** was born from a simple observation: **proprietary tools are volatile.** We've all seen the cycle: excellent tools attract early adopters with "unlimited" promises, only to alter the deal, cap productivity, or change Terms of Service overnight when the economics shift.

We believe a developer's environment should be **deterministic infrastructure**, not a shifting service. You shouldn't be penalized for being "too productive" — and your workflow shouldn't break because a company decides to pivot.

**Ronin is different.** We are removing the rent-seeking layer entirely. This is about trust, sovereignty, and code.
<!-- Plugin description end -->


## 🛡️ The Ronin Covenant

Unlike services that operate as "black boxes," Ronin operates on three non-negotiable rules designed to future-proof your workflow:

### 1. Sovereignty (Bring Your Own Key)
**You provide the brains; Ronin provides the body.** Connect directly to OpenAI, Anthropic, or DeepSeek using your own keys. 
* **No middleman markup.**
* **No "pooled capacity" limits.**
* **No opaque tiers.** As long as you have a key, Ronin works.

### 2. Privacy & Independence (Local-First)
A companion that relies 100% on the cloud isn't a tool; it's a dependency.
Ronin treats `localhost` as a first-class citizen. With native support for **Ollama** and local LLMs, you can build entirely offline. Your code never leaves your machine unless *you* decide to send it.

### 3. Radical Transparency (No Rug-Pulls)
Ronin is client-side software. We cannot downgrade your tier because **there is no tier**. We cannot revoke your access because the code lives on your machine.

> **"Reliability is not an optional feature. It is the baseline."**

## 🚀 Why This Matters

As engineers, we need tools that respect our expertise:

1.  **Deterministic:** Terms of Service shouldn't change based on a company's burn rate.
2.  **Transparent:** Limits should be explicit (defined by your API provider), not hidden behind vague definitions of "abuse."
3.  **Accessible:** By providing full access to the source code, we ensure this tool belongs to the community, forever.

Ronin doesn't have a hidden agenda or a "fair usage policy" designed to slow you down. **Ronin just wants to help you ship code.**

---
*Build freely.*

## 🏗️ Project Structure

The codebase is organized into clear functional components:

*   **`src/main/kotlin/com/ronin/actions`**: Entry points for user interactions (e.g., `ExplainCodeAction`, `FixCodeAction`).
*   **`src/main/kotlin/com/ronin/ui`**: Manages the Tool Window, Chat UI, and message history.
*   **`src/main/kotlin/com/ronin/service`**: The agent's core logic:
    *   **`LLMService`**: Communicates with AI providers (OpenAI, etc.).
    *   **`ContextService`**: Reads the active file and project structure to give the agent context.
    *   **`EditService`**: Safely modifies files in the editor using the IntelliJ SDK.
## 🧠 Agentic Architecture
 
Ronin is an autonomous loop rooted in the `ChatToolWindowFactory` but triggered via multiple entry points.
 
```mermaid
sequenceDiagram
    participant Action as Editor Action
    participant UI as Chat UI
    participant Context as ContextService
    participant LLM as LLMService
    participant Parser as ResponseParser
    participant Tools as Tools (Edit/Terminal)

    Action->>UI: explicit triggers (Explain/Fix)
    User->>UI: Manual Input
    
    loop Agentic Cycle
        UI->>Context: Gather Context (Active File + Tree)
        UI->>LLM: Send Prompt + History
        LLM-->>UI: Response (Text + Commands)
        
        UI->>Parser: Parse Response
        
        alt Has File Edit
            Parser->>Tools: EditService.replaceFileContent()
            Tools-->>UI: "📝 Created/Updated File"
        end
        
        alt Has Terminal Command
            Parser->>Tools: TerminalService.runCommand()
            Tools-->>UI: Stream Output
            UI->>LLM: Auto-FollowUp (Command Result)
        end
    end
```
 
## ⚙️ System Components
 
### Services (`src/main/kotlin/com/ronin/service`)
The nervous system of the agent.
 
| Service | Responsibility |
| :--- | :--- |
| **`LLMService`** | Manages API connections (OpenAI/o1). Handles model filtering, timeouts (5m for reasoning), and parameter optimization. |
| **`ContextService`** | "Eyes" of the agent. Reads the active file content and scans the project directory tree (ignoring `node_modules`, `build`, etc.) to provide spatial awareness. |
| **`EditService`** | "Hands" of the agent. Safely creates directories and modifies files using `WriteCommandAction`. Returns detailed success/failure feedback. |
| **`TerminalService`** | "Legs" of the agent. Executes shell commands, capturing stdout/stderr to feed back into the reasoning loop. |
| **`ResponseParser`** | The "Ear". Parses raw LLM output to detect intents like `[UPDATED_FILE]` or `[EXECUTE]`. |
| **`ChatStorageService`** | The "Memory". Persists chat history to `ronin_chat_history.xml` so context survives IDE restarts. |
 
### Actions (`src/main/kotlin/com/ronin/actions`)
Context-menu triggers that bootstrap the agent with specific intents.
 
*   **`ExplainCodeAction`**: Sends selected code with "Explain this..." prompt.
*   **`FixCodeAction`**: Sends selected code with "Fix bugs..." prompt.
*   **`ImproveCodeAction`**: Asks for refactoring ideas.
*   **`GenerateUnitTestsAction`**: Asks for test coverage.
*   **`BaseRoninAction`**: Abstract base that handles the pipeline: `Open Window -> Gather Context -> Send -> Apply`.
 
## 🗺️ Roadmap
 
### ✅ Architecture & Core
- [x] **Agentic Loop**: Autonomous `Command -> Execute -> Analyze` cycle.
- [x] **Context Awareness**: recursive project structure analysis and active file inputs.
- [x] **Persistence**: `ChatStorageService` saves history across IDE restarts (XML-based).
- [x] **Robust File Ops**: Create, edit, and fix files safely (`WriteCommandAction`) with feedback.
 
### ✅ LLM Capabilities
- [x] **OpenAI Integration**: Full support for `gpt-4o`, `gpt-4-turbo`.
- [x] **Advanced Reasoning**: Optimized support for `o1-preview` and `o1-mini` (Temperature 1, 5min timeout).
- [x] **Unlimited Context**: No arbitrary token limits (`max_tokens` removed); full model capacity enabled.
- [ ] **Multi-Provider**: Native support for Anthropic/Claude and Ollama (Currently Beta/Mocked).
 
### ✅ Developer Experience (DX)
- [x] **Integrated Terminal**: Execute shell commands directly from chat options.
- [x] **Responsive UI**: Fluid message bubbles (`GridBagLayout`) that respect window size.
- [x] **Smart Logs**: Command outputs are summarized in UI to prevent clutter, but sent fully to LLM.
- [ ] **Multimodal**: Drag-and-drop image support for visual debugging.