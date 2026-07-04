#include "core/config/overlay_config.h"

#include <INIReader.h>

#include "common/file_util.h"
#include "common/logging/log.h"

namespace Config {

OverlayConfig::OverlayConfig() {
    location =
        FileUtil::GetUserPath(FileUtil::UserPath::ConfigDir) +
        "azahar_input_layout.ini";

    if (!FileUtil::Exists(location)) {
        FileUtil::CreateFullPath(location);
        FileUtil::WriteStringToFile(true, location, "");
    }

    std::string buffer;
    FileUtil::ReadFileToString(true, location, buffer);

    ini = std::make_unique<INIReader>(buffer.c_str(), buffer.size());
}

float OverlayConfig::GetFloat(const std::string& key, float default_value) {
    if (ini) {
        return ini->GetFloat("Overlay", key, default_value);
    }

    auto it = values.find(key);
    if (it != values.end()) {
        return it->second;
    }

    return default_value;
}

void OverlayConfig::SetFloat(const std::string& key, float value) {
    values[key] = value;
}

void OverlayConfig::Save() {
    std::string out;
    out += "[Overlay]\n";

    for (const auto& [key, value] : values) {
        out += key + "=" + std::to_string(value) + "\n";
    }

    FileUtil::WriteStringToFile(true, location, out);
}

} // namespace Config
