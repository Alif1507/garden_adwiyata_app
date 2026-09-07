#include "PumpController.h"

#include <cstdlib>
#include <iostream>

using garden::Control;
using garden::Inputs;
using garden::Mode;
using garden::PumpController;
using garden::StopReason;

namespace {

int failures = 0;

#define CHECK(condition)                                                        \
    do {                                                                        \
        if (!(condition)) {                                                     \
            std::cerr << __FILE__ << ':' << __LINE__                            \
                      << " CHECK failed: " #condition << '\n';                 \
            ++failures;                                                        \
        }                                                                       \
    } while (false)

Inputs safeInputs(uint32_t nowMs = 0) {
    Inputs input{};
    input.nowMs = nowMs;
    input.unixSeconds = 2'000'000'000ULL;
    input.timeTrusted = true;
    input.wifiConnected = true;
    input.authenticated = true;
    input.controlValid = true;
    input.controlReceivedAtMs = nowMs;
    input.soilValid = true;
    input.soilPercent = 50;
    input.control = Control{Mode::Auto, 30, 45, 5'000};
    return input;
}

void testRelayDefaultsOff() {
    PumpController controller;
    CHECK(!controller.running());
}

void testAutoStartsAndStopsAtThresholds() {
    PumpController controller;
    Inputs input = safeInputs(100);
    input.soilPercent = 30;

    auto result = controller.update(input);
    CHECK(result.changed);
    CHECK(result.running);

    input.nowMs = 1'100;
    input.controlReceivedAtMs = 1'100;
    input.soilPercent = 45;
    result = controller.update(input);
    CHECK(result.changed);
    CHECK(!result.running);
    CHECK(result.stopReason == StopReason::SoilWet);
}

void testAutoCooldownStartsAfterEveryStop() {
    PumpController controller;
    Inputs input = safeInputs(0);
    input.control.mode = Mode::Manual;
    input.soilPercent = 20;
    input.manualGrant = true;
    input.manualRequestExpiresAt = input.unixSeconds + 10;

    CHECK(controller.update(input).running);

    input.manualGrant = false;
    input.nowMs = 5'000;
    input.controlReceivedAtMs = 5'000;
    auto result = controller.update(input);
    CHECK(!result.running);
    CHECK(result.stopReason == StopReason::ManualDurationComplete);

    input.control.mode = Mode::Auto;
    input.nowMs = 5'001;
    input.controlReceivedAtMs = 5'001;
    CHECK(!controller.update(input).running);

    input.nowMs = 64'999;
    input.controlReceivedAtMs = 64'999;
    CHECK(!controller.update(input).running);

    input.nowMs = 65'000;
    input.controlReceivedAtMs = 65'000;
    CHECK(controller.update(input).running);
}

void testHardStopAtExactlyFifteenSecondsInBothModes() {
    for (Mode mode : {Mode::Auto, Mode::Manual}) {
        PumpController controller;
        Inputs input = safeInputs(42);
        input.control.mode = mode;
        input.soilPercent = 10;
        if (mode == Mode::Manual) {
            input.control.manualDurationMs = 15'000;
            input.manualGrant = true;
            input.manualRequestExpiresAt = input.unixSeconds + 10;
        }
        CHECK(controller.update(input).running);

        input.manualGrant = false;
        input.nowMs = 15'041;
        input.controlReceivedAtMs = 15'041;
        CHECK(controller.update(input).running);

        input.nowMs = 15'042;
        input.controlReceivedAtMs = 15'042;
        const auto result = controller.update(input);
        CHECK(!result.running);
        CHECK(result.stopReason == StopReason::HardRuntimeLimit);
    }
}

void testManualNeedsFreshAcknowledgedGrantAndTrustworthyTime() {
    PumpController controller;
    Inputs input = safeInputs(100);
    input.control.mode = Mode::Manual;
    input.soilPercent = 20;

    CHECK(!controller.update(input).running);

    input.manualGrant = true;
    input.timeTrusted = false;
    input.manualRequestExpiresAt = input.unixSeconds + 10;
    CHECK(!controller.update(input).running);

    input.timeTrusted = true;
    input.manualRequestExpiresAt = input.unixSeconds;
    CHECK(!controller.update(input).running);

    input.manualRequestExpiresAt = input.unixSeconds + 1;
    CHECK(controller.update(input).running);
}

void testSafetyStopsImmediately() {
    struct UnsafeCase {
        const char *name;
        void (*makeUnsafe)(Inputs &);
        StopReason reason;
    };

    const UnsafeCase cases[] = {
        {"wifi", [](Inputs &i) { i.wifiConnected = false; }, StopReason::WifiLost},
        {"auth", [](Inputs &i) { i.authenticated = false; }, StopReason::AuthLost},
        {"control", [](Inputs &i) { i.controlValid = false; }, StopReason::InvalidControl},
        {"soil", [](Inputs &i) { i.soilValid = false; }, StopReason::InvalidSoil},
        {"mode", [](Inputs &i) { i.control.mode = Mode::Manual; }, StopReason::ModeChanged},
    };

    for (const auto &testCase : cases) {
        PumpController controller;
        Inputs input = safeInputs(0);
        input.soilPercent = 10;
        CHECK(controller.update(input).running);
        input.nowMs = 1;
        input.controlReceivedAtMs = 1;
        testCase.makeUnsafe(input);
        const auto result = controller.update(input);
        if (result.running || result.stopReason != testCase.reason) {
            std::cerr << "unsafe case failed: " << testCase.name << '\n';
            ++failures;
        }
    }
}

void testControlBecomesStaleAtExactlyTenSeconds() {
    PumpController controller;
    Inputs input = safeInputs(500);
    input.soilPercent = 10;
    CHECK(controller.update(input).running);

    input.nowMs = 10'499;
    CHECK(controller.update(input).running);

    input.nowMs = 10'500;
    const auto result = controller.update(input);
    CHECK(!result.running);
    CHECK(result.stopReason == StopReason::ControlStale);
}

void testInvalidThresholdsAndDurationPreventActivation() {
    const Control invalidControls[] = {
        {Mode::Auto, 45, 45, 5'000},
        {Mode::Auto, 70, 45, 5'000},
        {Mode::Manual, 30, 45, 999},
        {Mode::Manual, 30, 45, 15'001},
        {Mode::Invalid, 30, 45, 5'000},
    };

    for (const Control &control : invalidControls) {
        PumpController controller;
        Inputs input = safeInputs();
        input.soilPercent = 10;
        input.control = control;
        input.manualGrant = true;
        input.manualRequestExpiresAt = input.unixSeconds + 10;
        const auto result = controller.update(input);
        CHECK(!result.running);
        CHECK(result.stopReason == StopReason::InvalidControl);
    }
}

void testRawControlRequiresEveryField() {
    Control parsed{};
    CHECK(!garden::makeControl(Mode::Auto, -1, 45, 5'000, parsed));
    CHECK(!garden::makeControl(Mode::Auto, 30, -1, 5'000, parsed));
    CHECK(!garden::makeControl(Mode::Manual, 30, 45, -1, parsed));
    CHECK(garden::makeControl(Mode::Manual, 30, 45, 5'000, parsed));
    CHECK(parsed.mode == Mode::Manual);
    CHECK(parsed.moistureStart == 30);
    CHECK(parsed.moistureStop == 45);
    CHECK(parsed.manualDurationMs == 5'000);
}

void testManualRequestIdMustBeCanonicalUuid() {
    CHECK(garden::isCanonicalUuid("123e4567-e89b-12d3-a456-426614174000"));
    CHECK(garden::isCanonicalUuid("123E4567-E89B-12D3-A456-426614174000"));
    CHECK(!garden::isCanonicalUuid(""));
    CHECK(!garden::isCanonicalUuid("123e4567-e89b-12d3-a456"));
    CHECK(!garden::isCanonicalUuid("123e4567xe89b-12d3-a456-426614174000"));
    CHECK(!garden::isCanonicalUuid("123e4567-e89b-12d3-a45z-426614174000"));
}

void testMillisWrapDoesNotBreakHardStop() {
    PumpController controller;
    Inputs input = safeInputs(0xFFFFF000u);
    input.soilPercent = 10;
    CHECK(controller.update(input).running);

    input.nowMs = 0x00002A98u; // exactly 15,000 ms later
    input.controlReceivedAtMs = input.nowMs;
    const auto result = controller.update(input);
    CHECK(!result.running);
    CHECK(result.stopReason == StopReason::HardRuntimeLimit);
}

} // namespace

int main() {
    testRelayDefaultsOff();
    testAutoStartsAndStopsAtThresholds();
    testAutoCooldownStartsAfterEveryStop();
    testHardStopAtExactlyFifteenSecondsInBothModes();
    testManualNeedsFreshAcknowledgedGrantAndTrustworthyTime();
    testSafetyStopsImmediately();
    testControlBecomesStaleAtExactlyTenSeconds();
    testInvalidThresholdsAndDurationPreventActivation();
    testRawControlRequiresEveryField();
    testManualRequestIdMustBeCanonicalUuid();
    testMillisWrapDoesNotBreakHardStop();

    if (failures != 0) {
        std::cerr << failures << " check(s) failed\n";
        return EXIT_FAILURE;
    }
    std::cout << "All pump controller tests passed\n";
    return EXIT_SUCCESS;
}
