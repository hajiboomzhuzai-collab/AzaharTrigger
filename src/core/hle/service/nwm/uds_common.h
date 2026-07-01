// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include "core/hle/service/service.h"

namespace Service::NWM {

enum class ConnectionType : u8 {
    Client = 0x1,
    Spectator = 0x2,
}

constexpr u8 LEGACY_CONNECTION_CLIENT = 0x0;

}; // namespace Service::NWM
