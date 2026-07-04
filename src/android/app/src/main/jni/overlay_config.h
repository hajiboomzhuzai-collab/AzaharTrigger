#pragma once

#include <memory>
#include <string>

class INIReader;

namespace Config {

class OverlayConfig {
public:
    OverlayConfig();

    float GetFloat(const std::string& key, float default_value);
    void SetFloat(const std::string& key, float value);

    void Save();

private:
    std::string location;
    std::unique_ptr<INIReader> ini;
};

} // namespace Config
