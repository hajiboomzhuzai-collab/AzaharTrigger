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
}
