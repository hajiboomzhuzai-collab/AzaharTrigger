// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <QWidget>
#include <QLabel>
#include <QPushButton>
#include <QVBoxLayout>
#include <QTimer>
#include <memory>
#include <unordered_set>
#include <deque>
#include "network/network.h"

namespace Ui {
class FloatingChat;
}

class FloatingChat : public QWidget {
    Q_OBJECT

public:
    explicit FloatingChat(QWidget* parent = nullptr);
    ~FloatingChat();

    void SetPlayerList(const Network::RoomMember::MemberList& member_list);
    void Clear();
    void AppendStatusMessage(const QString& msg);
    void RetranslateUi();
    void SetModPerms(bool is_mod);
    void Enable();
    void Disable();

public slots:
    void OnRoomUpdate(const Network::RoomInformation& info);
    void OnChatReceive(const Network::ChatEntry&);
    void OnStatusMessageReceive(const Network::StatusMessageEntry&);
    void OnChatButtonClicked();
    void HideChatMessages();

signals:
    void ChatReceived(const Network::ChatEntry&);
    void StatusMessageReceived(const Network::StatusMessageEntry&);
    void UserPinged();

private:
    void AppendChatMessage(const QString&);
    void UpdateChatDisplay();
    bool ValidateMessage(const std::string&);

    QLabel* chat_display;
    QPushButton* chat_button;
    QVBoxLayout* main_layout;
    QTimer* auto_hide_timer;
    
    std::deque<QString> message_queue;
    bool is_enabled = true;
    bool has_mod_perms = false;
    bool is_visible = false;
    std::unordered_set<std::string> block_list;

    static constexpr u32 max_chat_lines = 12;
    static constexpr int AUTO_HIDE_DURATION = 5000; // 5 seconds in milliseconds
};

Q_DECLARE_METATYPE(Network::ChatEntry);
Q_DECLARE_METATYPE(Network::StatusMessageEntry);
Q_DECLARE_METATYPE(Network::RoomInformation);
