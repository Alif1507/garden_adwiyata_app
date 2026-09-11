#include "PumpController.h"

namespace garden {

namespace {

uint32_t elapsed(uint32_t now, uint32_t then) {
    return now - then;
}

bool isHex(char value) {
    return (value >= '0' && value <= '9') ||
           (value >= 'a' && value <= 'f') ||
           (value >= 'A' && value <= 'F');
}

} // namespace

bool isCanonicalUuid(const char *value) {
    if (value == nullptr) return false;
    for (int index = 0; index < 36; ++index) {
        if (value[index] == '\0') return false;
        const bool hyphen = index == 8 || index == 13 || index == 18 || index == 23;
        if (hyphen ? value[index] != '-' : !isHex(value[index])) return false;
    }
    return value[36] == '\0';
}

bool Control::valid() const {
    return (mode == Mode::Auto || mode == Mode::Manual) &&
           moistureStart < moistureStop && moistureStop <= 100 &&
           manualDurationMs >= 1'000 &&
           manualDurationMs <= kMaximumPumpRuntimeMs;
}

bool makeControl(Mode mode, int moistureStart, int moistureStop,
                 int manualDurationMs, Control &output) {
    if (moistureStart < 0 || moistureStart > 100 ||
        moistureStop < 0 || moistureStop > 100 || manualDurationMs < 0) {
        output = Control{};
        return false;
    }
    const Control candidate{mode, static_cast<uint8_t>(moistureStart),
                            static_cast<uint8_t>(moistureStop),
                            static_cast<uint32_t>(manualDurationMs)};
    if (!candidate.valid()) {
        output = Control{};
        return false;
    }
    output = candidate;
    return true;
}

StopReason PumpController::unsafeReason(const Inputs &input) const {
    if (!input.wifiConnected) {
        return StopReason::WifiLost;
    }
    if (!input.authenticated) {
        return StopReason::AuthLost;
    }
    if (!input.controlValid || !input.control.valid()) {
        return StopReason::InvalidControl;
    }
    if (elapsed(input.nowMs, input.controlReceivedAtMs) >= kControlStaleMs) {
        return StopReason::ControlStale;
    }
    if (!input.soilValid || input.soilPercent > 100) {
        return StopReason::InvalidSoil;
    }
    if (running_ && input.control.mode != startedMode_) {
        return StopReason::ModeChanged;
    }
    return StopReason::None;
}

Result PumpController::stopped(StopReason reason, uint32_t nowMs) {
    Result result{false, false, reason};
    if (running_) {
        running_ = false;
        hasStopped_ = true;
        lastStoppedAtMs_ = nowMs;
        startedMode_ = Mode::Invalid;
        manualDurationMs_ = 0;
        result.changed = true;
    }
    return result;
}

Result PumpController::started(Mode mode, uint32_t nowMs) {
    running_ = true;
    startedAtMs_ = nowMs;
    startedMode_ = mode;
    return Result{true, true, StopReason::None};
}

Result PumpController::update(const Inputs &input) {
    const StopReason safety = unsafeReason(input);
    if (safety != StopReason::None) {
        return stopped(safety, input.nowMs);
    }

    if (running_) {
        if (elapsed(input.nowMs, startedAtMs_) >= kMaximumPumpRuntimeMs) {
            return stopped(StopReason::HardRuntimeLimit, input.nowMs);
        }
        if (startedMode_ == Mode::Manual &&
            elapsed(input.nowMs, startedAtMs_) >= manualDurationMs_) {
            return stopped(StopReason::ManualDurationComplete, input.nowMs);
        }
        if (startedMode_ == Mode::Auto &&
            input.soilPercent >= input.control.moistureStop) {
            return stopped(StopReason::SoilWet, input.nowMs);
        }
        return Result{false, true, StopReason::None};
    }

    if (input.control.mode == Mode::Manual) {
        if (input.manualGrant && input.timeTrusted &&
            input.unixSeconds < input.manualRequestExpiresAt) {
            manualDurationMs_ = input.control.manualDurationMs;
            return started(Mode::Manual, input.nowMs);
        }
        return Result{false, false, StopReason::None};
    }

    const bool cooldownComplete =
        !hasStopped_ || elapsed(input.nowMs, lastStoppedAtMs_) >= kAutomaticCooldownMs;
    if (cooldownComplete && input.soilPercent <= input.control.moistureStart) {
        return started(Mode::Auto, input.nowMs);
    }
    return Result{false, false, StopReason::None};
}

} // namespace garden
