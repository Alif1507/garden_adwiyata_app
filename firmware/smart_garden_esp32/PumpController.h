#pragma once

#include <cstdint>

namespace garden {

constexpr uint32_t kMaximumPumpRuntimeMs = 15'000;
constexpr uint32_t kAutomaticCooldownMs = 60'000;
constexpr uint32_t kControlStaleMs = 10'000;

enum class Mode : uint8_t {
    Invalid,
    Auto,
    Manual,
};

enum class StopReason : uint8_t {
    None,
    WifiLost,
    AuthLost,
    InvalidControl,
    ControlStale,
    InvalidSoil,
    ModeChanged,
    SoilWet,
    ManualDurationComplete,
    HardRuntimeLimit,
};

struct Control {
    Mode mode = Mode::Invalid;
    uint8_t moistureStart = 30;
    uint8_t moistureStop = 45;
    uint32_t manualDurationMs = 5'000;

    bool valid() const;
};

bool makeControl(Mode mode, int moistureStart, int moistureStop,
                 int manualDurationMs, Control &output);

struct Inputs {
    uint32_t nowMs = 0;
    uint64_t unixSeconds = 0;
    bool timeTrusted = false;
    bool wifiConnected = false;
    bool authenticated = false;
    bool controlValid = false;
    uint32_t controlReceivedAtMs = 0;
    bool soilValid = false;
    uint8_t soilPercent = 0;
    Control control{};

    // A one-loop pulse emitted only after Firebase acknowledged and cleared the
    // corresponding persisted manual request.
    bool manualGrant = false;
    uint64_t manualRequestExpiresAt = 0;
};

struct Result {
    bool changed = false;
    bool running = false;
    StopReason stopReason = StopReason::None;
};

bool isCanonicalUuid(const char *value);

class PumpController {
  public:
    Result update(const Inputs &input);
    bool running() const { return running_; }

  private:
    Result stopped(StopReason reason, uint32_t nowMs);
    Result started(Mode mode, uint32_t nowMs);
    StopReason unsafeReason(const Inputs &input) const;

    bool running_ = false;
    bool hasStopped_ = false;
    uint32_t startedAtMs_ = 0;
    uint32_t lastStoppedAtMs_ = 0;
    Mode startedMode_ = Mode::Invalid;
    uint32_t manualDurationMs_ = 0;
};

} // namespace garden
