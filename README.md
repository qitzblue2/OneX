# NPC Coach (Fabric 1.21.1)

Fight an AI coach NPC in survival. After every fight the coach looks at your stats (accuracy, crits,
combos, strafing, damage, shield use...), tells you what to improve, answers your questions, and can
fight you in a style that trains your weakest area.

## Get a coach
- Craft a **Coach Spawn Egg**: stone sword + book + emerald (shapeless), or take it from the Spawn Eggs tab in creative.
- Right-click the coach (or press **K** near it) to open the menu.
- Sneak + right-click with an empty hand to pick the coach back up.

## Menu
- **Start Fight**: normal sparring. **Drill** + **Start Drill**: pick a style (defaults to the coach's recommendation).
- **Last Report**: the coach's analysis of your last fight. **Chat** / **Ask**: follow-up questions.
- **NPC-API-KEY** + **Save**: only visible to operators / the singleplayer host. The key is never sent to clients.

Drills: Normal, Rush (defense), Strafe (aim), Shield (shield-breaking/timing), Kite (chasing), Combo (crits/combos).

## NPC-API-KEY
One field, set in the menu, with `/npccoach key <keys>`, or in `config/npccoach.json`:

```json
{ "NPC-API-KEY": "gsk_..., sk-ant-..." }
```

Paste one key or several separated by commas. The provider is detected automatically:

| Prefix | Provider |
| --- | --- |
| `gsk_` | Groq (free tier is enough for ~50 fights a day) |
| `sk-ant-` | Claude |
| `sk-` | ChatGPT / OpenAI |
| `AIza` | Gemini |

With several keys the coach uses the first one and moves on to the next if a key fails or hits
`dailyLimitPerKey` (default 50 calls per key per day; questions count too). With no working key the
coach still gives a rule-based report. Models can be changed in the config (`groqModel`, `openaiModel`,
`claudeModel`, `geminiModel`).

## Commands
`/npccoach menu | start [drill] | ask <question> | stop | status | key <keys>` (key needs op).

## Build
`./gradlew build` → `build/libs/npc-coach-0.1.0.jar`. Install it with Fabric API on client and server.
