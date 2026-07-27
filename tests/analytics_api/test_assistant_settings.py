from __future__ import annotations

import pytest

from agriinsight.analytics_api.assistant_settings import (
    AssistantSettings,
    AssistantSettingsError,
)


def test_assistant_is_disabled_without_a_secret() -> None:
    settings = AssistantSettings.from_environment({})

    assert settings.enabled is False
    assert settings.api_key == ""
    assert settings.model == "deepseek-v4-flash"
    assert "api_key" not in repr(settings)


def test_enabled_assistant_requires_a_valid_local_secret() -> None:
    with pytest.raises(AssistantSettingsError, match="API key is required"):
        AssistantSettings.from_environment(
            {"AGRIINSIGHT_ASSISTANT_ENABLED": "true"}
        )

    with pytest.raises(AssistantSettingsError, match="invalid format"):
        AssistantSettings.from_environment(
            {
                "AGRIINSIGHT_ASSISTANT_ENABLED": "true",
                "AGRIINSIGHT_LLM_API_KEY": "unsafe\r\nheader",
            }
        )


@pytest.mark.parametrize(
    ("name", "value", "message"),
    [
        (
            "AGRIINSIGHT_LLM_BASE_URL",
            "https://attacker.invalid",
            "fixed DeepSeek origin",
        ),
        ("AGRIINSIGHT_LLM_MODEL", "deepseek-v4-pro", "V4 Flash"),
        ("AGRIINSIGHT_LLM_PROVIDER", "openai", "DeepSeek"),
        ("AGRIINSIGHT_ASSISTANT_MAX_EVIDENCE_ITEMS", "0", "evidence items"),
        ("AGRIINSIGHT_ASSISTANT_MAX_OUTPUT_TOKENS", "9000", "output token"),
        ("AGRIINSIGHT_ASSISTANT_MAX_CONCURRENT_REQUESTS", "0", "concurrent"),
    ],
)
def test_provider_and_cost_boundaries_fail_closed(
    name: str, value: str, message: str
) -> None:
    with pytest.raises(AssistantSettingsError, match=message):
        AssistantSettings.from_environment({name: value})


def test_enabled_assistant_accepts_the_verified_deepseek_contract() -> None:
    settings = AssistantSettings.from_environment(
        {
            "AGRIINSIGHT_ASSISTANT_ENABLED": "true",
            "AGRIINSIGHT_LLM_API_KEY": "test-only-key-material-000000",
            "AGRIINSIGHT_LLM_BASE_URL": "https://api.deepseek.com/",
            "AGRIINSIGHT_LLM_MODEL": "deepseek-v4-flash",
            "AGRIINSIGHT_LLM_PROVIDER": "deepseek",
        }
    )

    assert settings.enabled is True
    assert settings.base_url == "https://api.deepseek.com"
    assert settings.thinking_enabled is False
