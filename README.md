# SPDX-License-Identifier: Apache-2.0
# AegisGate Rampart JetBrains Plugin

AegisGate Rampart — JetBrains IDE integration for local AI security proxy detection.

## Features

- 🔐 PII detection (SSN, credit cards, emails, phone numbers)
- 🔑 Secret detection (API keys, tokens, passwords)
- ⚔️ XSS detection in AI-generated code
- 🧠 Prompt injection detection
- 📋 Compliance checking

## Requirements

- IntelliJ IDEA 2023.2+ or any JetBrains IDE 2023.2+
- AegisGate Rampart proxy running on localhost (default: `http://localhost:9090`)

## Installation

1. Install the plugin from JetBrains Marketplace (coming soon)
2. Or build from source: `./gradlew buildPlugin`
3. Install the resulting `.zip` from `build/distributions/`

## Configuration

Settings → Tools → AegisGate Rampart

- **Rampart proxy URL**: Default `http://localhost:9090`
- **Auto-scan on save**: Enabled by default
- **Minimum severity**: `medium` (critical, high, medium, low, info)

## Commands

- **Tools → Rampart → Scan Current File**: Manual scan trigger
- **Tools → Rampart → Check Connection**: Verify proxy connectivity
- **Tools → Rampart → Rampart Settings**: Open configuration

## Privacy

- All detection happens locally via the Rampart proxy
- No prompt text stored or sent externally
- No PII stored or forwarded
- Zero external dependencies
- Plugin talks ONLY to localhost

## Building

```bash
./gradlew buildPlugin
```

The plugin zip will be in `build/distributions/`.

## Testing

```bash
./gradlew test
```

## License

Apache-2.0