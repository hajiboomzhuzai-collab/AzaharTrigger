// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QTimer>
#include "citra_qt/multiplayer/floating_chat.h"
#include "common/logging/log.h"
#include "network/announce_multiplayer_session.h"

FloatingChat::FloatingChat(QWidget* parent) : QWidget(parent) {
    setWindowFlags(Qt::FramelessWindowHint | Qt::WindowStaysOnTopHint | Qt::ToolTip);
    setAttribute(Qt::WA_TranslucentBackground);
    
    main_layout = new QVBoxLayout(this);
    main_layout->setContentsMargins(8, 8, 8, 8);
    main_layout->setSpacing(4);

    // Chat display - shows messages
    chat_display = new QLabel(this);
    chat_display->setAlignment(Qt::AlignTop | Qt::AlignLeft);
    chat_display->setWordWrap(true);
    chat_display->setStyleSheet(
        "QLabel { color: #FFFFFF; font-size: 14px; font-family: Arial; background-color: transparent; }"
    );
    chat_display->setMaximumWidth(300);
    chat_display->setMinimumHeight(50);
    main_layout->addWidget(chat_display);

    // Chat button
    QHBoxLayout* button_layout = new QHBoxLayout();
    chat_button = new QPushButton("💬", this);
    chat_button->setMaximumWidth(50);
    chat_button->setMaximumHeight(40);
    chat_button->setStyleSheet(
        "QPushButton { background-color: rgba(100, 150, 255, 200); color: #FFFFFF; "
        "border: none; border-radius: 8px; font-size: 18px; }"
        "QPushButton:hover { background-color: rgba(100, 150, 255, 230); }"
    );
    button_layout->addWidget(chat_button);
    button_layout->addStretch();
    main_layout->addLayout(button_layout);

    // Auto-hide timer
    auto_hide_timer = new QTimer(this);
    auto_hide_timer->setSingleShot(true);
    connect(auto_hide_timer, &QTimer::timeout, this, &FloatingChat::HideChatMessages);

    // Register network types
    qRegisterMetaType<Network::ChatEntry>();
    qRegisterMetaType<Network::StatusMessageEntry>();
    qRegisterMetaType<Network::RoomInformation>();
    qRegisterMetaType<Network::RoomMember::State>();

    // Setup network callbacks
    if (auto member = Network::GetRoomMember().lock()) {
        member->BindOnChatMessageRecieved(
            [this](const Network::ChatEntry& chat) { emit ChatReceived(chat); });
        member->BindOnStatusMessageReceived(
            [this](const Network::StatusMessageEntry& status_message) {
                emit StatusMessageReceived(status_message);
            });
        connect(this, &FloatingChat::ChatReceived, this, &FloatingChat::OnChatReceive);
        connect(this, &FloatingChat::StatusMessageReceived, this,
                &FloatingChat::OnStatusMessageReceive);
    }

    // Connect button
    connect(chat_button, &QPushButton::clicked, this, &FloatingChat::OnChatButtonClicked);

    // Position in top-left corner
    move(10, 30);
    HideChatMessages();
}

FloatingChat::~FloatingChat() = default;

void FloatingChat::SetPlayerList(const Network::RoomMember::MemberList& member_list) {
    // Update player list if needed for future features
}

void FloatingChat::Clear() {
    message_queue.clear();
    chat_display->clear();
    block_list.clear();
}

void FloatingChat::AppendStatusMessage(const QString& msg) {
    AppendChatMessage(msg);
}

void FloatingChat::RetranslateUi() {
    // No UI strings to translate in this simple version
}

void FloatingChat::SetModPerms(bool is_mod) {
    has_mod_perms = is_mod;
}

void FloatingChat::Enable() {
    is_enabled = true;
}

void FloatingChat::Disable() {
    is_enabled = false;
}

bool FloatingChat::ValidateMessage(const std::string& msg) {
    return !msg.empty();
}

void FloatingChat::AppendChatMessage(const QString& msg) {
    // Add message to queue
    message_queue.push_back(msg);
    
    // Keep only the last 12 messages
    if (message_queue.size() > max_chat_lines) {
        message_queue.pop_front();
    }
    
    UpdateChatDisplay();
}

void FloatingChat::UpdateChatDisplay() {
    QString display_text;
    for (const auto& msg : message_queue) {
        if (!display_text.isEmpty()) {
            display_text += "\n";
        }
        display_text += msg;
    }
    chat_display->setText(display_text);
}

void FloatingChat::OnChatButtonClicked() {
    if (is_visible) {
        HideChatMessages();
    } else {
        // Show chat messages and set auto-hide timer
        chat_display->show();
        is_visible = true;
        auto_hide_timer->start(AUTO_HIDE_DURATION);
    }
}

void FloatingChat::HideChatMessages() {
    chat_display->hide();
    is_visible = false;
    auto_hide_timer->stop();
}

void FloatingChat::OnRoomUpdate(const Network::RoomInformation& info) {
    if (auto room_member = Network::GetRoomMember().lock()) {
        SetPlayerList(room_member->GetMemberInformation());
    }
}

void FloatingChat::OnChatReceive(const Network::ChatEntry& chat) {
    if (!ValidateMessage(chat.message)) {
        return;
    }
    if (auto room = Network::GetRoomMember().lock()) {
        auto members = room->GetMemberInformation();
        auto it = std::find_if(members.begin(), members.end(),
                               [&chat](const Network::RoomMember::MemberInformation& member) {
                                   return member.nickname == chat.nickname &&
                                          member.username == chat.username;
                               });
        if (it == members.end()) {
            LOG_INFO(Network, "Chat message received from unknown player. Ignoring it.");
            return;
        }
        if (block_list.count(chat.nickname)) {
            LOG_INFO(Network, "Chat message received from blocked player {}. Ignoring it.",
                     chat.nickname);
            return;
        }

        QString nickname = QString::fromStdString(chat.nickname);
        QString message = QString::fromStdString(chat.message);
        QString formatted = QString("%1: %2").arg(nickname, message);
        AppendChatMessage(formatted);
        
        // Automatically show chat for new messages and reset timer
        if (!is_visible) {
            chat_display->show();
            is_visible = true;
        }
        auto_hide_timer->start(AUTO_HIDE_DURATION);
        
        emit UserPinged();
    }
}

void FloatingChat::OnStatusMessageReceive(const Network::StatusMessageEntry& status_message) {
    QString name = QString::fromStdString(status_message.nickname);
    QString message;

    switch (status_message.type) {
    case Network::IdMemberJoin:
        message = tr("%1 has joined").arg(name);
        break;
    case Network::IdMemberLeave:
        message = tr("%1 has left").arg(name);
        break;
    case Network::IdMemberKicked:
        message = tr("%1 has been kicked").arg(name);
        break;
    case Network::IdMemberBanned:
        message = tr("%1 has been banned").arg(name);
        break;
    }

    if (!message.isEmpty()) {
        AppendStatusMessage(message);
        
        // Automatically show status messages and reset timer
        if (!is_visible) {
            chat_display->show();
            is_visible = true;
        }
        auto_hide_timer->start(AUTO_HIDE_DURATION);
    }
}
