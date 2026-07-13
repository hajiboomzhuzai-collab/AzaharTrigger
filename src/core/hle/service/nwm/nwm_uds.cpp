// Copyright Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <algorithm>
#include <cstring>
#include <boost/serialization/list.hpp>
#include <boost/serialization/map.hpp>
#include <cryptopp/osrng.h>
#include "common/archives.h"
#include "common/common_types.h"
#include "common/hacks/hack_manager.h"
#include "common/logging/log.h"
#include "common/settings.h"
#include "core/core.h"
#include "core/core_timing.h"
#include "core/hle/ipc_helpers.h"
#include "core/hle/kernel/event.h"
#include "core/hle/kernel/shared_memory.h"
#include "core/hle/kernel/shared_page.h"
#include "core/hle/result.h"
#include "core/hle/service/cfg/cfg.h"
#include "core/hle/service/cfg/cfg_u.h"
#include "core/hle/service/nwm/nwm_uds.h"
#include "core/hle/service/nwm/uds_beacon.h"
#include "core/hle/service/nwm/uds_connection.h"
#include "core/hle/service/nwm/uds_data.h"
#include "core/memory.h"

SERIALIZE_EXPORT_IMPL(Service::NWM::NWM_UDS)
SERVICE_CONSTRUCT_IMPL(Service::NWM::NWM_UDS)

namespace {

constexpr auto NODE_TIMEOUT = std::chrono::seconds(15);

} // anonymous namespace

namespace Service::NWM {

template <class Archive>
void NWM_UDS::serialize(Archive& ar, const unsigned int) {
    DEBUG_SERIALIZATION_POINT;
    ar& boost::serialization::base_object<Kernel::SessionRequestHandler>(*this);
    ar & node_map;
    ar & node_lookup;
    ar & connection_event;
    ar & received_beacons;
    // wifi_packet_received set in constructor
}

namespace ErrCodes {
enum {
    NotInitialized = 2,
    WrongStatus = 490,
};
} // namespace ErrCodes

// Number of beacons to store before we start dropping the old ones.
// TODO(Subv): Find a more accurate value for this limit.
constexpr std::size_t MaxBeaconFrames = 15;

// Network node id used when a SecureData packet is addressed to every connected node.
constexpr u16 BroadcastNetworkNodeId = 0xFFFF;

// The Host has always dest_node_id 1
constexpr u16 HostDestNodeId = 1;

std::list<Network::WifiPacket> NWM_UDS::GetReceivedBeacons(const MacAddress& sender) {
    std::scoped_lock lock(beacon_mutex);

    if (sender != Network::BroadcastMac) {
        std::list<Network::WifiPacket> filtered_list;

        const auto beacon = std::find_if(
            received_beacons.begin(),
            received_beacons.end(),
            [&sender](const Network::WifiPacket& packet) {
                return packet.transmitter_address == sender;
            });

        if (beacon != received_beacons.end()) {
            filtered_list.push_back(*beacon);
            received_beacons.erase(beacon);
        }

        return filtered_list;
    }

    std::list<Network::WifiPacket> result(
        received_beacons.begin(),
        received_beacons.end());

    received_beacons.clear();

    return result;
}

/// Sends a WifiPacket to the room we're currently connected to.
void SendPacket(Network::WifiPacket& packet) {
    const bool important_packet =
        packet.type != Network::WifiPacket::PacketType::Data;

    if (important_packet) {
        LOG_ERROR(Service_NWM,
                  "TX type={} ch={} size={} dst={:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                  static_cast<u32>(packet.type),
                  packet.channel,
                  packet.data.size(),
                  packet.destination_address[0],
                  packet.destination_address[1],
                  packet.destination_address[2],
                  packet.destination_address[3],
                  packet.destination_address[4],
                  packet.destination_address[5]);
    }

    if (auto room_member = Network::GetRoomMember().lock()) {
        const auto state = room_member->GetState();

        if (state == Network::RoomMember::State::Joined ||
            state == Network::RoomMember::State::Moderator) {

            packet.transmitter_address = room_member->GetMacAddress();

            if (important_packet) {
                LOG_ERROR(Service_NWM,
                          "TX sender={:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                          packet.transmitter_address[0],
                          packet.transmitter_address[1],
                          packet.transmitter_address[2],
                          packet.transmitter_address[3],
                          packet.transmitter_address[4],
                          packet.transmitter_address[5]);
            }

            room_member->SendWifiPacket(packet);

            if (important_packet) {
                LOG_ERROR(Service_NWM,
                          "TX SENT type={} size={}",
                          static_cast<u32>(packet.type),
                          packet.data.size());
            }

        } else {
            LOG_ERROR(Service_NWM,
                      "TX FAILED room state={}",
                      static_cast<u32>(state));
        }

    } else {
        LOG_ERROR(Service_NWM,
                  "TX FAILED no RoomMember");
    }
}

u16 NWM_UDS::GetNextAvailableNodeId() {
    for (u16 index = 0; index < connection_status.max_nodes; ++index) {
        if ((connection_status.node_bitmask & (1 << index)) == 0)
            return index + 1;
    }

    // Any connection attempts to an already full network should have been refused.
    UNREACHABLE_MSG("No available connection slots in the network");
    return 0;
}

void NWM_UDS::BroadcastNodeMap() {
    // Note: This is not how UDS on a 3ds does it but it shouldn't be
    // necessary for citra
    Network::WifiPacket packet;
    packet.channel = network_channel;
    packet.type = Network::WifiPacket::PacketType::NodeMap;
    packet.destination_address = Network::BroadcastMac;
    auto node_can_broad = [](auto& node) -> bool {
        return node.second.connected && !node.second.spec;
    };
    std::size_t num_entries =
        std::count_if(node_map.begin(), node_map.end(),
                      [&node_can_broad](const auto& node) { return node_can_broad(node); });
    using node_t = decltype(node_map)::value_type;
    packet.data.resize(sizeof(num_entries) +
                       (sizeof(node_t::first) + sizeof(node_t::second.node_id)) * num_entries);
    std::memcpy(packet.data.data(), &num_entries, sizeof(num_entries));
    std::size_t offset = sizeof(num_entries);
    for (const auto& node : node_map) {
        if (node_can_broad(node)) {
            std::memcpy(packet.data.data() + offset, node.first.data(), sizeof(node.first));
            std::memcpy(packet.data.data() + offset + sizeof(node.first), &node.second.node_id,
                        sizeof(node.second.node_id));
            offset += sizeof(node.first) + sizeof(node.second.node_id);
        }
    }
    LOG_ERROR(Service_NWM,
          "HOST >>> BroadcastNodeMap entries={} node_map={} total_nodes={} bitmask=0x{:X}",
          num_entries,
          node_map.size(),
          connection_status.total_nodes,
          connection_status.node_bitmask);

for (const auto& [mac, node] : node_map) {
    LOG_ERROR(Service_NWM,
              "HOST NODEMAP id={} connected={} spec={} mac={:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
              node.node_id,
              node.connected,
              node.spec,
              mac[0], mac[1], mac[2],
              mac[3], mac[4], mac[5]);
}
    LOG_ERROR(Service_NWM,
    "HOST >>> Sending NodeMap packet type={} entries={} bytes={}",
    static_cast<int>(packet.type),
    num_entries,
    packet.data.size());

    SendPacket(packet);
}

void NWM_UDS::HandleNodeMapPacket(const Network::WifiPacket& packet) {
    std::scoped_lock lock(connection_status_mutex);

    if (connection_status.status == NetworkStatus::ConnectedAsHost) {
        return;
    }

    std::size_t num_entries;
    std::memcpy(&num_entries, packet.data.data(), sizeof(num_entries));

    LOG_ERROR(Service_NWM,
              "CLIENT <<< NodeMap entries={} total_nodes={} status={} node_map_before={}",
              num_entries,
              connection_status.total_nodes,
              static_cast<u32>(connection_status.status),
              node_map.size());


    if (num_entries == 0) {
        LOG_ERROR(Service_NWM,
                  "CLIENT ignoring empty NodeMap");
        return;
    }


    LOG_ERROR(Service_NWM,
              "CLIENT merging NodeMap existing_nodes={}",
              node_map.size());


    Network::MacAddress address;
    u16 id;
    std::size_t offset = sizeof(num_entries);


    u16 new_bitmask = 0;
    u16 new_changed_nodes = 0;
    u8 new_total_nodes = 0;


    for (std::size_t i = 0; i < num_entries; i++) {

        if (offset + sizeof(address) + sizeof(id) > packet.data.size()) {
            LOG_ERROR(Service_NWM,
                      "CLIENT NodeMap packet truncated offset={} size={}",
                      offset,
                      packet.data.size());
            return;
        }


        std::memcpy(&address,
                    packet.data.data() + offset,
                    sizeof(address));

        std::memcpy(&id,
                    packet.data.data() + offset + sizeof(address),
                    sizeof(id));


        auto& node = node_map[address];


        if (node.connected && node.node_id != id) {
            LOG_ERROR(Service_NWM,
                      "CLIENT NodeMap updating MAC old_id={} new_id={}",
                      node.node_id,
                      id);
        }


        node.connected = true;
        node.reconnecting = false;
        node.spec = false;
        node.node_id = id;
        node.last_seen = std::chrono::steady_clock::now();



        if (id != NodeIDSpec && id <= UDSMaxNodes) {

            node_lookup[id] = address;

            new_bitmask |= (1 << id);
            new_changed_nodes |= (1 << id);

            new_total_nodes++;


            LOG_ERROR(Service_NWM,
                      "CLIENT lookup updated id={} reconnect_ready=true",
                      id);
        }



        LOG_ERROR(Service_NWM,
                  "CLIENT NodeMap entry={} id={} mac={:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                  i,
                  id,
                  address[0],
                  address[1],
                  address[2],
                  address[3],
                  address[4],
                  address[5]);


        offset += sizeof(address) + sizeof(id);
    }


    /*
     * Sync connection status.
     *
     * This fixes the case:
     *
     * status=6 node_id=1 total_nodes=0
     *
     * where SecureData arrives before status knows about nodes.
     */

    connection_status.total_nodes = new_total_nodes;
    connection_status.node_bitmask = new_bitmask;
    connection_status.changed_nodes = new_changed_nodes;



    LOG_ERROR(Service_NWM,
              "CLIENT NodeMap DONE node_map={} total_nodes={} bitmask=0x{:X} lookup_ready={}",
              node_map.size(),
              connection_status.total_nodes,
              static_cast<u16>(connection_status.node_bitmask),
              std::count_if(node_lookup.begin(),
                            node_lookup.end(),
                            [](const auto& e) {
                                return e.has_value();
                            }));



    for (u16 i = 1; i <= UDSMaxNodes; i++) {

        if (node_lookup[i]) {

            LOG_ERROR(Service_NWM,
          "LOOKUP id={0} mac={1:02X}:{2:02X}:{3:02X}:{4:02X}:{5:02X}:{6:02X}",
          static_cast<u32>(i),
          static_cast<u32>((*node_lookup[i])[0]),
          static_cast<u32>((*node_lookup[i])[1]),
          static_cast<u32>((*node_lookup[i])[2]),
          static_cast<u32>((*node_lookup[i])[3]),
          static_cast<u32>((*node_lookup[i])[4]),
          static_cast<u32>((*node_lookup[i])[5]));
        }
    }


    connection_status_event->Signal();
}

void NWM_UDS::HandleBeaconFrame(const Network::WifiPacket& packet) {
    std::scoped_lock lock(beacon_mutex);
    const auto unique_beacon =
        std::find_if(received_beacons.begin(), received_beacons.end(),
                     [&packet](const Network::WifiPacket& new_packet) {
                         return new_packet.transmitter_address == packet.transmitter_address;
                     });
    if (unique_beacon != received_beacons.end()) {
        // We already have a beacon from the same mac in the deque, remove the old one;
        received_beacons.erase(unique_beacon);
    }

    received_beacons.emplace_back(packet);

    // Discard old beacons if the buffer is full.
    if (received_beacons.size() > MaxBeaconFrames)
        received_beacons.pop_front();
}

void NWM_UDS::HandleAssociationResponseFrame(const Network::WifiPacket& packet) {
    auto assoc_result = GetAssociationResult(packet.data);

    ASSERT_MSG(std::get<AssocStatus>(assoc_result) == AssocStatus::Successful,
               "Could not join network");
    {
        std::scoped_lock lock(connection_status_mutex);
        if (connection_status.status != NetworkStatus::Connecting) {
            LOG_DEBUG(Service_NWM,
                      "Ignored AssociationResponseFrame because connection status is {}",
                      static_cast<u32>(connection_status.status));
            return;
        }
    }

    // Send the EAPoL-Start packet to the server.
    using Network::WifiPacket;
    WifiPacket eapol_start;
    eapol_start.channel = network_channel;
    eapol_start.data =
        GenerateEAPoLStartFrame(std::get<u16>(assoc_result), conn_type, current_node);
    // TODO(B3N30): Encrypt the packet.
    eapol_start.destination_address = packet.transmitter_address;
    eapol_start.type = WifiPacket::PacketType::Data;

    SendPacket(eapol_start);
}

void NWM_UDS::HandleEAPoLPacket(const Network::WifiPacket& packet) {
    std::scoped_lock lock{connection_status_mutex, system.Kernel().GetHLELock()};

    if (GetEAPoLFrameType(packet.data) == EAPoLStartMagic) {
        if (connection_status.status != NetworkStatus::ConnectedAsHost) {
            LOG_DEBUG(Service_NWM, "Connection sequence aborted, because connection status is {}",
                      static_cast<u32>(connection_status.status));
            return;
        }

        auto node_it = node_map.find(packet.transmitter_address);
        if (node_it == node_map.end()) {
            LOG_DEBUG(Service_NWM, "Connection sequence aborted, because the AuthenticationFrame "
                                   "of the client wasn't recieved");
            return;
        }
        if (node_it->second.connected) {
            LOG_DEBUG(Service_NWM,
                      "Connection sequence aborted, because the client is already connected");
            return;
        }

        ASSERT(connection_status.max_nodes != connection_status.total_nodes);

        auto eapol_start = ParseCompatibleEAPoLStart(packet.data);

        auto node = DeserializeNodeInfo(eapol_start.packet.node);

        bool is_reconnect = false;

auto existing = node_map.find(packet.transmitter_address);
if (existing != node_map.end() &&
    existing->second.reconnecting &&
    existing->second.node_id != NodeIDSpec) {

    is_reconnect = true;

    LOG_ERROR(Service_NWM,
              "HOST: reconnect request old_node_id={}",
              existing->second.node_id);
}

        if (eapol_start.packet.connection_type == ConnectionType::Client) {
            // Get an unused network node id
            u16 node_id;

if (is_reconnect) {
    node_id = existing->second.node_id;

    LOG_ERROR(Service_NWM,
              "HOST RECONNECT: reusing node_id={}",
              node_id);
} else {
    node_id = GetNextAvailableNodeId();

    LOG_ERROR(Service_NWM,
              "HOST NEW CLIENT: assigning node_id={}",
              node_id);

    connection_status.total_nodes++;
    network_info.total_nodes++;
}

node.network_node_id = node_id;

connection_status.node_bitmask |= 1 << (node_id - 1);
connection_status.changed_nodes |= 1 << (node_id - 1);
connection_status.nodes[node_id - 1] = node_id;

node_info[node_id - 1] = node;

auto& host_node = node_map[packet.transmitter_address];
host_node.node_id = node_id;
host_node.connected = true;
host_node.reconnecting = false;
host_node.spec = false;
host_node.last_seen = std::chrono::steady_clock::now();

node_lookup[node_id] = packet.transmitter_address;

LOG_ERROR(Service_NWM,
          "HOST STATE: total_nodes={} bitmask=0x{:X}",
          connection_status.total_nodes,
          connection_status.node_bitmask);

BroadcastNodeMap();
            LOG_ERROR(Service_NWM,
          "HOST AFTER JOIN: total_nodes={} node_map={} bitmask=0x{:X}",
          connection_status.total_nodes,
          node_map.size(),
          connection_status.node_bitmask);
        } else if (eapol_start.packet.connection_type == ConnectionType::Spectator) {
            auto& spec_node = node_map[packet.transmitter_address];
spec_node.node_id = NodeIDSpec;
spec_node.connected = true;
spec_node.reconnecting = false;
spec_node.spec = true;
spec_node.last_seen = std::chrono::steady_clock::now();

        } else {
            LOG_ERROR(Service_NWM, "Client tried connecting with unknown connection type: 0x{:x}",
                      static_cast<u32>(eapol_start.packet.connection_type));
        }

        // Send the EAPoL-Logoff packet.
        using Network::WifiPacket;
        WifiPacket eapol_logoff;
        eapol_logoff.channel = network_channel;
        eapol_logoff.data =
            GenerateEAPoLLogoffFrame(packet.transmitter_address, node.network_node_id, node_info,
                                     network_info.max_nodes, network_info.total_nodes);
        // TODO(Subv): Encrypt the packet.

        // TODO(B3N30): send the eapol packet just to the new client and implement a proper
        // broadcast packet for all other clients
        // On a 3ds the eapol packet is only sent to packet.transmitter_address
        // while a packet containing the node information is broadcasted
        // For now we will broadcast the eapol packet instead
        eapol_logoff.destination_address = Network::BroadcastMac;
        eapol_logoff.type = WifiPacket::PacketType::Data;

        SendPacket(eapol_logoff);

        connection_status_event->Signal();
    } else if (connection_status.status == NetworkStatus::Connecting) {
        auto logoff = ParseEAPoLLogoffFrame(packet.data);

        network_info.host_mac_address = packet.transmitter_address;
        LOG_ERROR(Service_NWM,
          "EAPOL UPDATE: connected_nodes={} assigned_node={} bitmask(before)=0x{:X}",
          logoff.connected_nodes,
          connection_status.network_node_id,
          connection_status.node_bitmask);
        network_info.total_nodes = logoff.connected_nodes;
        network_info.max_nodes = logoff.max_nodes;

        connection_status.network_node_id = logoff.assigned_node_id;

LOG_ERROR(Service_NWM,
          "CLIENT ASSIGNED NODE: {}",
          connection_status.network_node_id);

LOG_ERROR(Service_NWM,
          "CLIENT: total_nodes {} -> {} (received EAPOL)",
          connection_status.total_nodes,
          logoff.connected_nodes);

connection_status.total_nodes = logoff.connected_nodes;
        connection_status.max_nodes = logoff.max_nodes;

        node_info.clear();
        node_info.resize(network_info.max_nodes);
        for (const auto& node : logoff.nodes) {
            const u16 index = node.network_node_id;
            if (!index) {
                continue;
            }

            connection_status.node_bitmask |= 1 << (index - 1);
            connection_status.changed_nodes |= 1 << (index - 1);
            connection_status.nodes[index - 1] = index;

            node_info[index - 1] = DeserializeNodeInfo(node);
        }

        if (conn_type == ConnectionType::Client) {
            connection_status.status = NetworkStatus::ConnectedAsClient;
        } else if (conn_type == ConnectionType::Spectator) {
            connection_status.status = NetworkStatus::ConnectedAsSpectator;
        } else {
            LOG_ERROR(Service_NWM, "Unknown connection type: 0x{:x}", static_cast<u32>(conn_type));
        }

        if (auto* node = FindNodeByNodeId(connection_status.network_node_id)) {
    node->connected = true;
    node->reconnecting = false;
    node->last_seen = std::chrono::steady_clock::now();
        }

        // We're now connected, signal the application
        connection_status.status_change_reason = NetworkStatusChangeReason::ConnectionEstablished;
        // Some games require ConnectToNetwork to block, for now it doesn't
        // If blocking is implemented this lock needs to be changed,
        // otherwise it might cause deadlocks
        connection_status_event->Signal();
        connection_event->Signal();
    } else if (connection_status.status == NetworkStatus::ConnectedAsClient ||
           connection_status.status == NetworkStatus::ConnectedAsSpectator) {

    LOG_ERROR(Service_NWM,
              "EAPOL UPDATE START: status={} total_nodes(before)={} bitmask(before)=0x{:X}",
              static_cast<u32>(connection_status.status),
              connection_status.total_nodes,
              connection_status.node_bitmask);

    // TODO(B3N30): Remove that section and send/receive a proper connection_status packet
    // On a 3ds this packet wouldn't be addressed to already connected clients
    // We use this information because in the current implementation the host
    // isn't broadcasting the node information
    auto logoff = ParseEAPoLLogoffFrame(packet.data);

    LOG_ERROR(Service_NWM,
              "EAPOL host says total_nodes={} max_nodes={}",
              logoff.connected_nodes,
              logoff.max_nodes);

    network_info.total_nodes = logoff.connected_nodes;
network_info.max_nodes = logoff.max_nodes;

connection_status.total_nodes = logoff.connected_nodes;
connection_status.max_nodes = logoff.max_nodes;

LOG_ERROR(Service_NWM,
          "CLIENT: total_nodes {} -> {} max_nodes={}",
          connection_status.total_nodes,
          logoff.connected_nodes,
          logoff.max_nodes);

std::memset(connection_status.nodes, 0, sizeof(connection_status.nodes));

const auto old_bitmask = connection_status.node_bitmask;
connection_status.node_bitmask = 0;

node_info.clear();
node_info.resize(connection_status.max_nodes);;

    for (const auto& node : logoff.nodes) {
        const u16 index = node.network_node_id;
        if (!index) {
            continue;
        }

        LOG_ERROR(Service_NWM,
                  "EAPOL NODE id={}",
                  index);

        connection_status.node_bitmask |= 1 << (index - 1);
        connection_status.nodes[index - 1] = index;

        node_info[index - 1] = DeserializeNodeInfo(node);
    }

    connection_status.changed_nodes = old_bitmask ^ connection_status.node_bitmask;
        
    LOG_ERROR(Service_NWM,
              "EAPOL UPDATE DONE: total_nodes={} bitmask(after)=0x{:X} changed=0x{:X}",
              connection_status.total_nodes,
              connection_status.node_bitmask,
              connection_status.changed_nodes);
    if (auto* node = FindNodeByNodeId(connection_status.network_node_id)) {
    node->connected = true;
    node->reconnecting = false;
    node->last_seen = std::chrono::steady_clock::now();
    }
        
    connection_status_event->Signal();
}
}

NWM_UDS::Node* NWM_UDS::FindNodeByNodeId(u16 node_id) {
    if (node_id == 0 || node_id > UDSMaxNodes) {
        LOG_ERROR(Service_NWM,
                  "FindNodeByNodeId invalid id={}",
                  node_id);
        return nullptr;
    }

    if (!node_lookup[node_id]) {
        LOG_ERROR(Service_NWM,
                  "FindNodeByNodeId lookup missing id={} status={} node_map={}",
                  node_id,
                  static_cast<u32>(connection_status.status),
                  node_map.size());

        return nullptr;
    }

    const auto& lookup_mac = *node_lookup[node_id];

    auto it = node_map.find(lookup_mac);

    if (it == node_map.end()) {
        LOG_ERROR(Service_NWM,
                  "FindNodeByNodeId map missing id={} node_map_size={}",
                  node_id,
                  node_map.size());

        return nullptr;
    }

    return &it->second;
}

void NWM_UDS::HandleSecureDataPacket(const Network::WifiPacket& packet) {
    const auto secure_data = ParseSecureDataHeader(packet.data);

    std::scoped_lock lock{connection_status_mutex, system.Kernel().GetHLELock()};

    auto* node = FindNodeByNodeId(secure_data.src_node_id);

    if (!node) {
        LOG_ERROR(Service_NWM,
                  "RECONNECT: unknown source node id={} recovering",
                  static_cast<u16>(secure_data.src_node_id));

        auto& recovered_node = node_map[packet.transmitter_address];

        recovered_node.connected = true;
        recovered_node.reconnecting = false;
        recovered_node.spec = false;
        recovered_node.node_id = secure_data.src_node_id;
        recovered_node.last_seen = std::chrono::steady_clock::now();

        if (secure_data.src_node_id <= UDSMaxNodes) {
            node_lookup[static_cast<u16>(secure_data.src_node_id)] =
                packet.transmitter_address;
        }

        LOG_ERROR(Service_NWM,
                  "RECONNECT: restored node id={} mac={:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                  static_cast<u16>(secure_data.src_node_id),
                  packet.transmitter_address[0],
                  packet.transmitter_address[1],
                  packet.transmitter_address[2],
                  packet.transmitter_address[3],
                  packet.transmitter_address[4],
                  packet.transmitter_address[5]);

        node = &recovered_node;
    }


    node->last_seen = std::chrono::steady_clock::now();
    node->connected = true;
    node->reconnecting = false;


    if (connection_status.status != NetworkStatus::ConnectedAsHost &&
        connection_status.status != NetworkStatus::ConnectedAsClient &&
        connection_status.status != NetworkStatus::ConnectedAsSpectator) {

        LOG_ERROR(Service_NWM,
                  "SECUREDATA DROP: not connected status={}",
                  static_cast<u32>(connection_status.status));

        return;
    }


    if (secure_data.src_node_id == connection_status.network_node_id) {
        return;
    }


    if (secure_data.dest_node_id != connection_status.network_node_id &&
        secure_data.dest_node_id != BroadcastNetworkNodeId) {

        if (packet.destination_address != Network::BroadcastMac &&
            connection_status.status != NetworkStatus::ConnectedAsHost) {

            LOG_ERROR(Service_NWM,
                      "SECUREDATA DROP: wrong destination dst={} my_node={}",
                      static_cast<u32>(secure_data.dest_node_id),
                      static_cast<u32>(connection_status.network_node_id));

            return;
        }


        if (connection_status.status == NetworkStatus::ConnectedAsHost &&
            secure_data.dest_node_id != BroadcastNetworkNodeId) {

            Network::WifiPacket out_packet = packet;
            out_packet.destination_address = Network::BroadcastMac;

            SendPacket(out_packet);
        }

        return;
    }


    ASSERT(!secure_data.is_management);


    auto channel_info = channel_data.find(secure_data.data_channel);


    if (channel_info == channel_data.end()) {

        LOG_ERROR(Service_NWM,
                  "SECUREDATA UNKNOWN CHANNEL={} "
                  "status={} node={} total_nodes={} channel_count={}",
                  static_cast<u32>(secure_data.data_channel),
                  static_cast<u32>(connection_status.status),
                  static_cast<u32>(connection_status.network_node_id),
                  connection_status.total_nodes,
                  channel_data.size());


        for (const auto& [ch, data] : channel_data) {

            LOG_ERROR(Service_NWM,
                      "CHANNEL TABLE ch={} bind={} node={}",
                      static_cast<u32>(ch),
                      data.bind_node_id,
                      data.network_node_id);
        }


        return;
    }


    if (channel_info->second.network_node_id != BroadcastNetworkNodeId &&
        channel_info->second.network_node_id != secure_data.src_node_id) {

        LOG_ERROR(Service_NWM,
                  "SECUREDATA BIND MISMATCH expected={} got={}",
                  static_cast<u32>(channel_info->second.network_node_id),
                  static_cast<u32>(secure_data.src_node_id));

        return;
    }


    channel_info->second.received_packets.emplace_back(packet.data);


    LOG_ERROR(Service_NWM,
              "SECUREDATA QUEUED "
              "channel={} queue_size={} src={} dst={} size={}",
              static_cast<u32>(secure_data.data_channel),
              channel_info->second.received_packets.size(),
              static_cast<u32>(secure_data.src_node_id),
              static_cast<u32>(secure_data.dest_node_id),
              secure_data.GetActualDataSize());


    channel_info->second.event->Signal();
}

void NWM_UDS::StartConnectionSequence(const MacAddress& server) {
    using Network::WifiPacket;
    WifiPacket auth_request;
    {
        std::scoped_lock lock(connection_status_mutex);
        connection_status.status = NetworkStatus::Connecting;

        // TODO(Subv): Handle timeout.

        // Send an authentication frame with SEQ1
        auth_request.channel = network_channel;
        auth_request.data = GenerateAuthenticationFrame(AuthenticationSeq::SEQ1);
        auth_request.destination_address = server;
        auth_request.type = WifiPacket::PacketType::Authentication;
    }

    SendPacket(auth_request);
}

void NWM_UDS::SendAssociationResponseFrame(const MacAddress& address) {
    using Network::WifiPacket;
    WifiPacket assoc_response;

    {
        std::scoped_lock lock(connection_status_mutex);
        if (connection_status.status != NetworkStatus::ConnectedAsHost) {
            LOG_ERROR(Service_NWM, "Connection sequence aborted, because connection status is {}",
                      static_cast<u32>(connection_status.status));
            return;
        }

        assoc_response.channel = network_channel;
        // TODO(Subv): This will cause multiple clients to end up with the same association id, but
        // we're not using that for anything.
        u16 association_id = 1;
        assoc_response.data = GenerateAssocResponseFrame(AssocStatus::Successful, association_id,
                                                         network_info.network_id);
        assoc_response.destination_address = address;
        assoc_response.type = WifiPacket::PacketType::AssociationResponse;
    }

    SendPacket(assoc_response);
}

void NWM_UDS::HandleAuthenticationFrame(const Network::WifiPacket& packet) {
    // Only the SEQ1 auth frame is handled here, the SEQ2 frame doesn't need any special behavior
    if (GetAuthenticationSeqNumber(packet.data) == AuthenticationSeq::SEQ1) {

        LOG_ERROR(Service_NWM,
                  "AUTH START: RX authentication request from "
                  "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                  packet.transmitter_address[0],
                  packet.transmitter_address[1],
                  packet.transmitter_address[2],
                  packet.transmitter_address[3],
                  packet.transmitter_address[4],
                  packet.transmitter_address[5]);

        using Network::WifiPacket;

        AuthenticationFrame auth_request;
        memcpy(&auth_request, packet.data.data(), sizeof(auth_request));

        WifiPacket auth_response;

        {
            std::scoped_lock lock(connection_status_mutex);

            LOG_ERROR(Service_NWM,
                      "AUTH STATE: status={} total_nodes={}/{} node_map={}",
                      static_cast<u32>(connection_status.status),
                      connection_status.total_nodes,
                      connection_status.max_nodes,
                      node_map.size());

            if (connection_status.status != NetworkStatus::ConnectedAsHost) {
                LOG_ERROR(Service_NWM,
                          "AUTH ABORT: not hosting (status={})",
                          static_cast<u32>(connection_status.status));
                return;
            }

            auto it = node_map.find(packet.transmitter_address);

            // Remove previous entry if this device is reconnecting.
            if (it != node_map.end()) {

                LOG_ERROR(Service_NWM,
                          "AUTH: Removing existing node before reconnect "
                          "{:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X} "
                          "node_id={}",
                          packet.transmitter_address[0],
                          packet.transmitter_address[1],
                          packet.transmitter_address[2],
                          packet.transmitter_address[3],
                          packet.transmitter_address[4],
                          packet.transmitter_address[5],
                          static_cast<u32>(it->second.node_id));

                if (it->second.node_id != 0 &&
                    it->second.node_id < node_lookup.size()) {

                    node_lookup[it->second.node_id].reset();
                }

                node_map.erase(it);
            }

            if (connection_status.max_nodes == connection_status.total_nodes) {
                LOG_ERROR(Service_NWM,
                          "AUTH ABORT: maximum nodes reached ({}/{})",
                          connection_status.total_nodes,
                          connection_status.max_nodes);
                return;
            }

            LOG_ERROR(Service_NWM,
                      "AUTH ACCEPT: inserting temporary node into node_map");

            // Respond with authentication response frame SEQ2
            auth_response.channel = network_channel;
            auth_response.data =
                GenerateAuthenticationFrame(AuthenticationSeq::SEQ2);
            auth_response.destination_address =
                packet.transmitter_address;
            auth_response.type =
                WifiPacket::PacketType::Authentication;

            auto& node = node_map[packet.transmitter_address];

            node.connected = false;
            node.reconnecting = false;
            node.spec = false;
            node.last_seen = std::chrono::steady_clock::now();

            LOG_ERROR(Service_NWM,
                      "AUTH NODE_MAP after insert: size={}",
                      node_map.size());
        }

        LOG_ERROR(Service_NWM,
                  "AUTH TX: Sending Authentication SEQ2");

        SendPacket(auth_response);

        LOG_ERROR(Service_NWM,
                  "AUTH TX: Sending Association Response");

        SendAssociationResponseFrame(packet.transmitter_address);

        LOG_ERROR(Service_NWM,
                  "AUTH END");
    }
}

void NWM_UDS::HandleDeauthenticationFrame(const Network::WifiPacket& packet) {
    LOG_ERROR(Service_NWM,
              "Ignoring DEAUTH from {:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X} (timeout test)",
              packet.transmitter_address[0],
              packet.transmitter_address[1],
              packet.transmitter_address[2],
              packet.transmitter_address[3],
              packet.transmitter_address[4],
              packet.transmitter_address[5]);

    std::scoped_lock lock{connection_status_mutex, system.Kernel().GetHLELock()};

    if (connection_status.status != NetworkStatus::ConnectedAsHost) {
        LOG_ERROR(Service_NWM, "Ignoring DEAUTH because we are not the host");
        return;
    }

    auto node_it = node_map.find(packet.transmitter_address);
    if (node_it == node_map.end()) {
        LOG_ERROR(Service_NWM, "Ignoring DEAUTH from unknown node");
        return;
    }

    const Node& node = node_it->second;

    LOG_ERROR(Service_NWM,
              "DEAUTH ignored from node {}. Waiting for timeout instead.",
              node.node_id);

    // ============================================================
    // Timeout experiment:
    // Do NOT erase the node.
    // Do NOT call Reset().
    // Do NOT signal connection_status_event.
    //
    // We want to see if the game disconnects the player naturally
    // after missing keepalive/heartbeat packets.
    // ============================================================

    return;
}

void NWM_UDS::HandleDataFrame(const Network::WifiPacket& packet) {
    LOG_DEBUG(Service_NWM,
        "HandleDataFrame(): from {:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X} size={}",
            packet.transmitter_address[0],
            packet.transmitter_address[1],
            packet.transmitter_address[2],
            packet.transmitter_address[3],
            packet.transmitter_address[4],
            packet.transmitter_address[5],
            packet.data.size());

    switch (GetFrameEtherType(packet.data)) {
    case EtherType::EAPoL:
        LOG_DEBUG(Service_NWM, "HandleDataFrame(): EAPoL");
        HandleEAPoLPacket(packet);
        break;

    case EtherType::SecureData:
        LOG_DEBUG(Service_NWM, "HandleDataFrame(): SecureData");
        HandleSecureDataPacket(packet);
        break;

    default:
        LOG_WARNING(Service_NWM,
            "HandleDataFrame(): Unknown EtherType");
        break;
    }
}

/// Callback to parse and handle a received wifi packet.
void NWM_UDS::OnWifiPacketReceived(const Network::WifiPacket& packet) {
    if (!initialized) {
        return;
    }

    LOG_ERROR(Service_NWM,
              "RX PACKET type={} ch={} size={} node_map={} channels={}",
              static_cast<u32>(packet.type),
              packet.channel,
              packet.data.size(),
              node_map.size(),
              channel_data.size());


    switch (packet.type) {

    case Network::WifiPacket::PacketType::Beacon:
        HandleBeaconFrame(packet);
        break;


    case Network::WifiPacket::PacketType::Authentication:
        LOG_ERROR(Service_NWM, "RX AUTH frame");
        HandleAuthenticationFrame(packet);
        break;


    case Network::WifiPacket::PacketType::AssociationResponse:
        LOG_ERROR(Service_NWM, "RX ASSOC RESPONSE");
        HandleAssociationResponseFrame(packet);
        break;


    case Network::WifiPacket::PacketType::Data:
        HandleDataFrame(packet);
        break;


    case Network::WifiPacket::PacketType::Deauthentication:
        LOG_ERROR(Service_NWM,
                  "RX DEAUTH");
        HandleDeauthenticationFrame(packet);
        break;


    case Network::WifiPacket::PacketType::NodeMap:
        LOG_ERROR(Service_NWM,
                  "RX NODEMAP before channels={}",
                  channel_data.size());

        HandleNodeMapPacket(packet);

        LOG_ERROR(Service_NWM,
                  "RX NODEMAP after channels={}",
                  channel_data.size());
        break;
    }
}

boost::optional<Network::MacAddress> NWM_UDS::GetNodeMacAddress(u16 dest_node_id, u8 flags) {
    constexpr u8 BroadcastFlag = 0x2;
    if ((flags & BroadcastFlag) || dest_node_id == BroadcastNetworkNodeId) {
        // Broadcast
        return Network::BroadcastMac;
    } else if (dest_node_id == HostDestNodeId) {
        // Destination is host
        return network_info.host_mac_address;
    }
    // Destination is a specific client
    auto destination =
        std::find_if(node_map.begin(), node_map.end(), [dest_node_id](const auto& node) {
            return node.second.node_id == dest_node_id && node.second.connected;
        });
    if (destination == node_map.end()) {
        return {};
    }
    return destination->first;
}

void NWM_UDS::ShutdownHLE() {
    initialized = false;

    for (auto& bind_node : channel_data) {
        bind_node.second.event->Signal();
    }
    channel_data.clear();
    node_map.clear();

    recv_buffer_memory.reset();
}

void NWM_UDS::Shutdown(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    ShutdownHLE();

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
    rb.Push(ResultSuccess);
    LOG_DEBUG(Service_NWM, "called");
}

void NWM_UDS::RecvBeaconBroadcastData(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    u32 out_buffer_size = rp.Pop<u32>();

    // scan input struct
    u32 unk1 = rp.Pop<u32>();
    u32 unk2 = rp.Pop<u32>();

    MacAddress mac_address;
    rp.PopRaw(mac_address);

    // uninitialized data in scan input struct
    rp.Skip(9, false);

    // end scan input struct

    u32 wlan_comm_id = rp.Pop<u32>();
    u32 id = rp.Pop<u32>();
    // From 3dbrew:
    // 'Official user processes create a new event handle which is then passed to this command.
    // However, those user processes don't save that handle anywhere afterwards.'
    // So we don't save/use that event too.
    std::shared_ptr<Kernel::Event> input_event = rp.PopObject<Kernel::Event>();

    Kernel::MappedBuffer& out_buffer = rp.PopMappedBuffer();
    ASSERT(out_buffer.GetSize() == out_buffer_size);

    std::size_t cur_buffer_size = sizeof(BeaconDataReplyHeader);

    auto beacons = GetReceivedBeacons(mac_address);

    BeaconDataReplyHeader data_reply_header{};
    data_reply_header.total_entries = static_cast<u32>(beacons.size());
    data_reply_header.max_output_size = out_buffer_size;

    // Write each of the received beacons into the buffer
    for (const auto& beacon : beacons) {
        BeaconEntryHeader entry{};
        // TODO(Subv): Figure out what this size is used for.
        entry.unk_size = static_cast<u32>(sizeof(BeaconEntryHeader) + beacon.data.size());
        entry.total_size = static_cast<u32>(sizeof(BeaconEntryHeader) + beacon.data.size());
        entry.wifi_channel = beacon.channel;
        entry.header_size = sizeof(BeaconEntryHeader);
        entry.mac_address = beacon.transmitter_address;

        ASSERT(cur_buffer_size < out_buffer_size);

        out_buffer.Write(&entry, cur_buffer_size, sizeof(BeaconEntryHeader));
        cur_buffer_size += sizeof(BeaconEntryHeader);
        const unsigned char* beacon_data = beacon.data.data();
        out_buffer.Write(beacon_data, cur_buffer_size, beacon.data.size());
        cur_buffer_size += beacon.data.size();
    }

    // Update the total size in the structure and write it to the buffer again.
    data_reply_header.total_size = static_cast<u32>(cur_buffer_size);
    out_buffer.Write(&data_reply_header, 0, sizeof(BeaconDataReplyHeader));

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 2);
    rb.Push(ResultSuccess);
    rb.PushMappedBuffer(out_buffer);

    // on a real 3ds this is about 0.38 seconds
    static constexpr std::chrono::nanoseconds UDSBeaconScanInterval{300000000};

    ctx.SleepClientThread("uds::RecvBeaconBroadcastData", UDSBeaconScanInterval, nullptr);

    LOG_DEBUG(Service_NWM,
              "called out_buffer_size=0x{:08X}, wlan_comm_id=0x{:08X}, id=0x{:08X},"
              "unk1=0x{:08X}, unk2=0x{:08X}, offset={}",
              out_buffer_size, wlan_comm_id, id, unk1, unk2, cur_buffer_size);
}

ResultVal<std::shared_ptr<Kernel::Event>> NWM_UDS::Initialize(
    u32 sharedmem_size, const NodeInfo& node, u16 version,
    std::shared_ptr<Kernel::SharedMemory> sharedmem) {

    current_node = node;
    initialized = true;

    recv_buffer_memory = std::move(sharedmem);
    ASSERT_MSG(recv_buffer_memory->GetSize() == sharedmem_size, "Invalid shared memory size.");

    {
        std::scoped_lock lock(connection_status_mutex);

        // Reset the connection status, it contains all zeros after initialization,
        // except for the actual status value.
        connection_status = {};
        connection_status.status = NetworkStatus::NotConnected;
        node_info.clear();
        node_info.push_back(current_node);
        channel_data.clear();
    }

    return connection_status_event;
}

// allows people who haven't set up their
// 3ds to play local play on games which
// require a unique friend code seed
void NWM_UDS::CheckSpoofFriendCodeSeed(Kernel::HLERequestContext& ctx, NodeInfo& node) {
    u64 caller_tid = ctx.ClientThread()->owner_process.lock()->codeset->program_id;
    if (Common::Hacks::hack_manager.GetHackAllowMode(
            Common::Hacks::HackType::SPOOF_FRIEND_CODE_SEED, caller_tid,
            Common::Hacks::HackAllowMode::DISALLOW) == Common::Hacks::HackAllowMode::FORCE) {
        auto mac_address = GetMacAddress();
        memcpy(&node.friend_code_seed, mac_address.data(), mac_address.size());
    }
}

void NWM_UDS::InitializeWithVersion(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    u32 sharedmem_size = rp.Pop<u32>();
    auto node = rp.PopRaw<NodeInfo>();
    u16 version = rp.Pop<u16>();
    auto sharedmem = rp.PopObject<Kernel::SharedMemory>();

    CheckSpoofFriendCodeSeed(ctx, node);

    auto result = Initialize(sharedmem_size, node, version, std::move(sharedmem));

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 2);
    rb.Push(result.Code());
    rb.PushCopyObjects(result.ValueOr(nullptr));

    LOG_DEBUG(Service_NWM, "called sharedmem_size=0x{:08X}, version=0x{:08X}", sharedmem_size,
              version);
}

void NWM_UDS::InitializeDeprecated(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    u32 sharedmem_size = rp.Pop<u32>();
    auto node = rp.PopRaw<NodeInfo>();
    auto sharedmem = rp.PopObject<Kernel::SharedMemory>();

    CheckSpoofFriendCodeSeed(ctx, node);

    // The deprecated version uses fixed 0x100 as the version
    auto result = Initialize(sharedmem_size, node, 0x100, std::move(sharedmem));

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 2);
    rb.Push(result.Code());
    rb.PushCopyObjects(result.ValueOr(nullptr));

    LOG_DEBUG(Service_NWM, "called sharedmem_size=0x{:08X}", sharedmem_size);
}

ConnectionStatus NWM_UDS::GetConnectionStatusHLE() {
    std::scoped_lock lock(connection_status_mutex);

    LOG_ERROR(Service_NWM,
              "GetConnectionStatusHLE status={} node_id={} total_nodes={} bitmask=0x{:X} changed=0x{:X}",
              static_cast<u32>(connection_status.status),
              connection_status.network_node_id,
              connection_status.total_nodes,
              connection_status.node_bitmask,
              connection_status.changed_nodes);

    ConnectionStatus cs_out = connection_status;

    // Reset the bitmask of changed nodes after each call to this
    // function to prevent falsely informing games of outstanding
    // changes in subsequent calls.
    connection_status.changed_nodes = 0;

    return cs_out;
}

void NWM_UDS::GetConnectionStatus(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    IPC::RequestBuilder rb = rp.MakeBuilder(13, 0);

    rb.Push(ResultSuccess);
    rb.PushRaw(GetConnectionStatusHLE());

    LOG_DEBUG(Service_NWM, "called");
}

std::unique_ptr<NodeInfo> NWM_UDS::GetNodeInformationHLE(u16 network_node_id) {
    std::scoped_lock lock(connection_status_mutex);

    LOG_ERROR(Service_NWM,
              "GetNodeInformationHLE request={} total_nodes={} bitmask=0x{:X}",
              static_cast<u32>(network_node_id),
              connection_status.total_nodes,
              connection_status.node_bitmask);

    auto itr =
        std::find_if(node_info.begin(), node_info.end(),
                     [network_node_id](const NodeInfo& node) {
                         return node.network_node_id == network_node_id;
                     });

    if (itr == node_info.end()) {
        LOG_ERROR(Service_NWM,
                  "GetNodeInformationHLE node {} NOT FOUND",
                  static_cast<u32>(network_node_id));
        return nullptr;
    }

    LOG_ERROR(Service_NWM,
          "GetNodeInformationHLE FOUND node={} friend_code_seed={}",
          static_cast<u32>(itr->network_node_id),
          itr->friend_code_seed);

    return std::make_unique<NodeInfo>(*itr);
}

void NWM_UDS::GetNodeInformation(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    u16 network_node_id = rp.Pop<u16>();

    if (!initialized) {
        IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
        rb.Push(Result(ErrorDescription::NotInitialized, ErrorModule::UDS,
                       ErrorSummary::StatusChanged, ErrorLevel::Status));
        return;
    }

    {
        auto node = GetNodeInformationHLE(network_node_id);
        if (!node) {
            IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
            rb.Push(Result(ErrorDescription::NotFound, ErrorModule::UDS,
                           ErrorSummary::WrongArgument, ErrorLevel::Status));
            return;
        }

        IPC::RequestBuilder rb = rp.MakeBuilder(11, 0);
        rb.Push(ResultSuccess);
        rb.PushRaw<NodeInfo>(*node);
    }
    LOG_DEBUG(Service_NWM, "called");
}

std::pair<ResultStatus, std::shared_ptr<Kernel::Event>> NWM_UDS::BindHLE(
    u32 bind_node_id,
    u32 recv_buffer_size,
    u8 data_channel,
    u16 network_node_id) {

    if (data_channel == 0 || bind_node_id == 0) {
        LOG_WARNING(Service_NWM,
                    "BIND invalid args channel={} bind_node={}",
                    static_cast<u32>(data_channel),
                    bind_node_id);

        return std::make_pair(ResultStatus::BindError_ArgsZero, nullptr);
    }


    constexpr std::size_t MaxBindNodes = 16;

    if (channel_data.size() >= MaxBindNodes) {
        LOG_WARNING(Service_NWM,
                    "BIND failed: max bind nodes reached size={}",
                    channel_data.size());

        return std::make_pair(ResultStatus::BindError_MaxBinds, nullptr);
    }


    constexpr u32 MinRecvBufferSize = 0x5F4;

    if (recv_buffer_size < MinRecvBufferSize) {
        LOG_WARNING(Service_NWM,
                    "BIND failed: recv buffer too small size={}",
                    recv_buffer_size);

        return std::make_pair(ResultStatus::BindError_RecvBufferTooLarge,
                              nullptr);
    }


    auto event = system.Kernel().CreateEvent(
        Kernel::ResetType::OneShot,
        "NWM::BindNodeEvent" + std::to_string(bind_node_id));


    {
        std::scoped_lock lock(connection_status_mutex);


        auto old_channel = channel_data.find(data_channel);

        if (old_channel != channel_data.end()) {

            LOG_ERROR(Service_NWM,
                      "BIND replacing existing channel={} "
                      "old_bind={} old_node={}",
                      static_cast<u32>(data_channel),
                      old_channel->second.bind_node_id,
                      old_channel->second.network_node_id);


            old_channel->second.event->Signal();
            channel_data.erase(old_channel);
        }


        channel_data[data_channel] = {
            bind_node_id,
            data_channel,
            network_node_id,
            event
        };


        LOG_ERROR(Service_NWM,
                  "BIND SUCCESS channel={} bind={} node={} total_channels={}",
                  static_cast<u32>(data_channel),
                  bind_node_id,
                  network_node_id,
                  channel_data.size());


        LOG_ERROR(Service_NWM,
                  "BIND CHANNEL TABLE:");

        for (const auto& [ch, data] : channel_data) {

            LOG_ERROR(Service_NWM,
                      "  ch={} bind={} node={}",
                      static_cast<u32>(ch),
                      data.bind_node_id,
                      data.network_node_id);
        }
    }


    return std::make_pair(ResultStatus::ResultSuccess,
                          std::move(event));
}

void NWM_UDS::Bind(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    u32 bind_node_id = rp.Pop<u32>();
    u32 recv_buffer_size = rp.Pop<u32>();
    u8 data_channel = rp.Pop<u8>();
    u16 network_node_id = rp.Pop<u16>();

    auto [ret, event] = BindHLE(bind_node_id, recv_buffer_size, data_channel, network_node_id);

    switch (ret) {
    case ResultStatus::BindError_ArgsZero: {
        IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
        rb.Push(Result(ErrorDescription::NotAuthorized, ErrorModule::UDS,
                       ErrorSummary::WrongArgument, ErrorLevel::Usage));
        return;
    }
    case ResultStatus::BindError_MaxBinds: {
        IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
        rb.Push(Result(ErrorDescription::OutOfMemory, ErrorModule::UDS, ErrorSummary::OutOfResource,
                       ErrorLevel::Status));
        return;
    }
    case ResultStatus::BindError_RecvBufferTooLarge: {
        IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
        rb.Push(Result(ErrorDescription::TooLarge, ErrorModule::UDS, ErrorSummary::WrongArgument,
                       ErrorLevel::Usage));
        return;
    }
    default:;
    }

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 2);
    rb.Push(ResultSuccess);
    rb.PushCopyObjects(event);
}

void NWM_UDS::UnbindHLE(u32 bind_node_id) {
    std::scoped_lock lock(connection_status_mutex);

    auto itr =
        std::find_if(channel_data.begin(), channel_data.end(), [bind_node_id](const auto& data) {
            return data.second.bind_node_id == bind_node_id;
        });

    if (itr != channel_data.end()) {
        // TODO(B3N30): Check out what Unbind does if the bind_node_id wasn't in the map
        itr->second.event->Signal();
        channel_data.erase(itr);
    }
}

void NWM_UDS::Unbind(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    u32 bind_node_id = rp.Pop<u32>();
    if (bind_node_id == 0) {
        IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
        rb.Push(Result(ErrorDescription::NotAuthorized, ErrorModule::UDS,
                       ErrorSummary::WrongArgument, ErrorLevel::Usage));
        return;
    }

    UnbindHLE(bind_node_id);

    IPC::RequestBuilder rb = rp.MakeBuilder(5, 0);
    rb.Push(ResultSuccess);
    rb.Push(bind_node_id);
    // TODO(B3N30): Find out what the other return values are
    rb.Push<u32>(0);
    rb.Push<u32>(0);
    rb.Push<u32>(0);
}

Result NWM_UDS::BeginHostingNetwork(std::span<const u8> network_info_buffer,
                                    std::vector<u8> passphrase) {
    // TODO(Subv): Store the passphrase and verify it when attempting a connection.

    {
        std::scoped_lock lock(connection_status_mutex);
        network_info = {};
        std::memcpy(&network_info, network_info_buffer.data(), network_info_buffer.size());

        // The real UDS module throws a fatal error if this assert fails.
        ASSERT_MSG(network_info.max_nodes > 1, "Trying to host a network of only one member.");

        connection_status.status = NetworkStatus::ConnectedAsHost;
        connection_status.status_change_reason = NetworkStatusChangeReason::ConnectionEstablished;

        // Ensure the application data size is less than the maximum value.
        ASSERT_MSG(network_info.application_data_size <= ApplicationDataSize,
                   "Data size is too big.");

        // Set up basic information for this network.
        network_info.oui_value = NintendoOUI;
        network_info.oui_type = static_cast<u8>(NintendoTagId::NetworkInfo);

        connection_status.max_nodes = network_info.max_nodes;

        // Resize the nodes list to hold max_nodes.
        node_info.clear();
        node_info.resize(network_info.max_nodes);

        // There's currently only one node in the network (the host).
        connection_status.total_nodes = 1;
        network_info.total_nodes = 1;

        // The host is always the first node
        connection_status.network_node_id = 1;
        current_node.network_node_id = 1;
        connection_status.nodes[0] = connection_status.network_node_id;
        // Set the bit 0 in the nodes bitmask to indicate that node 1 is already taken.
        connection_status.node_bitmask |= 1;
        // Notify the application that the first node was set.
        connection_status.changed_nodes |= 1;

        network_info.host_mac_address = GetMacAddress();

        node_info[0] = current_node;

        // If the game has a preferred channel, use that instead.
        if (network_info.channel != 0)
            network_channel = network_info.channel;
        else
            network_info.channel = DefaultNetworkChannel;
    }

    connection_status_event->Signal();

    // Start broadcasting the network, send a beacon frame every 102.4ms.
    system.CoreTiming().ScheduleEvent(msToCycles(DefaultBeaconInterval * MillisecondsPerTU),
                                      beacon_broadcast_event, 0);

    return ResultSuccess;
}

void NWM_UDS::BeginHostingNetwork(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    const u32 passphrase_size = rp.Pop<u32>();

    const std::vector<u8> network_info_buffer = rp.PopStaticBuffer();
    ASSERT(network_info_buffer.size() == sizeof(NetworkInfo));
    std::vector<u8> passphrase = rp.PopStaticBuffer();
    ASSERT(passphrase.size() == passphrase_size);

    LOG_DEBUG(Service_NWM, "called");
    auto result = BeginHostingNetwork(network_info_buffer, std::move(passphrase));
    LOG_DEBUG(Service_NWM, "An UDS network has been created.");

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
    rb.Push(result);
}

void NWM_UDS::BeginHostingNetworkDeprecated(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    // Real NWM module reads 0x108 bytes from the command buffer into the network info, where the
    // last 0xCC bytes (application_data and size) are undefined values. Here we just read the first
    // 0x3C defined bytes and zero application_data in BeginHostingNetwork.
    const auto network_info_buffer = rp.PopRaw<std::array<u8, 0x3C>>();
    const u32 passphrase_size = rp.Pop<u32>();
    std::vector<u8> passphrase = rp.PopStaticBuffer();
    ASSERT(passphrase.size() == passphrase_size);

    LOG_DEBUG(Service_NWM, "called");
    auto result = BeginHostingNetwork(network_info_buffer, std::move(passphrase));
    LOG_DEBUG(Service_NWM, "An UDS network has been created.");

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
    rb.Push(result);
}

Result NWM_UDS::EjectClientHLE(u16 network_node_id) {
    // The host can not be kicked.
    if (network_node_id == 1) {
        return Result(ErrorDescription::NotAuthorized, ErrorModule::UDS,
                      ErrorSummary::WrongArgument, ErrorLevel::Usage);
    }

    std::scoped_lock lock(connection_status_mutex);
    if (connection_status.status != NetworkStatus::ConnectedAsHost) {
        // Only the host can kick people.
        LOG_WARNING(Service_NWM, "called with status {}", connection_status.status);
        return Result(ErrorDescription::NotAuthorized, ErrorModule::UDS, ErrorSummary::InvalidState,
                      ErrorLevel::Usage);
    }

    using Network::WifiPacket;
    Network::MacAddress dest_address = Network::BroadcastMac;

    if (network_node_id != BroadcastNetworkNodeId) {
        auto address = GetNodeMacAddress(network_node_id, 0);

        if (!address) {
            // There is no error if the network node id was not found.
            return ResultSuccess;
        }
        dest_address = *address;
    }

    WifiPacket deauth;
    deauth.channel = network_channel;
    deauth.destination_address = dest_address;
    deauth.type = WifiPacket::PacketType::Deauthentication;
    SendPacket(deauth);
    if (network_node_id == BroadcastNetworkNodeId) {
        SendPacket(deauth);
        SendPacket(deauth);
    }

    // This function always returns success if the status is valid.
    return ResultSuccess;
}

void NWM_UDS::EjectClient(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    const u16 network_node_id = rp.Pop<u16>();

    LOG_WARNING(Service_NWM, "(stubbed) called");

    auto res = EjectClientHLE(network_node_id);

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
    rb.Push(res);
}

Result NWM_UDS::UpdateNetworkAttributeHLE(u16 node_bitmask, u8 flag) {
    [[maybe_unused]] constexpr u8 flag_disconnect_and_block_non_bitmasked_nodes = 0x1;

    // stubbed

    return ResultSuccess;
}

void NWM_UDS::UpdateNetworkAttribute(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    u16 bitmask = rp.Pop<u16>();
    u8 flag = rp.Pop<u8>();

    auto res = UpdateNetworkAttributeHLE(bitmask, flag);

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
    rb.Push(ResultSuccess);
}

Result NWM_UDS::DestroyNetworkHLE() {
    // Unschedule the beacon broadcast event.
    system.CoreTiming().UnscheduleEvent(beacon_broadcast_event, 0);

    // Only a host can destroy
    std::scoped_lock lock(connection_status_mutex);
    if (connection_status.status != NetworkStatus::ConnectedAsHost) {
        LOG_WARNING(Service_NWM, "called with status {}",
                    static_cast<u32>(connection_status.status));
        return Result(ErrCodes::WrongStatus, ErrorModule::UDS, ErrorSummary::InvalidState,
                      ErrorLevel::Status);
    }

    // TODO(B3N30): Send 3 Deauth packets

    u16_le tmp_node_id = connection_status.network_node_id;
    connection_status = {};
    connection_status.status = NetworkStatus::NotConnected;
    connection_status.network_node_id = tmp_node_id;
    node_map.clear();
    connection_status_event->Signal();

    for (auto& bind_node : channel_data) {
        bind_node.second.event->Signal();
    }
    channel_data.clear();

    return ResultSuccess;
}

void NWM_UDS::DestroyNetwork(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    auto res = DestroyNetworkHLE();

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);

    rb.Push(res);

    LOG_DEBUG(Service_NWM, "called");
}

void NWM_UDS::SendTo(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    rp.Skip(1, false);
    u16 dest_node_id = rp.Pop<u16>();
    u8 data_channel = rp.Pop<u8>();
    rp.Skip(1, false);
    u32 data_size = rp.Pop<u32>();
    u8 flags = rp.Pop<u8>();

    std::vector<u8> input_buffer = rp.PopStaticBuffer();

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);

    auto res = SendToHLE(dest_node_id, data_channel, data_size, flags, input_buffer);

    switch (res) {
    case ResultStatus::SendError_PacketSizeTooLarge:
        rb.Push(Result(ErrorDescription::TooLarge, ErrorModule::UDS, ErrorSummary::WrongArgument,
                       ErrorLevel::Usage));
        return;
    case ResultStatus::SendError_NotConnected:
        rb.Push(Result(ErrorDescription::NotAuthorized, ErrorModule::UDS,
                       ErrorSummary::InvalidState, ErrorLevel::Status));
        return;
    case ResultStatus::SendError_BadNode:
    case ResultStatus::SendError_BadMacAddress:
        rb.Push(Result(ErrorDescription::NotFound, ErrorModule::UDS, ErrorSummary::WrongArgument,
                       ErrorLevel::Status));
        return;
    default:;
    }

    rb.Push(ResultSuccess);
}

ResultStatus NWM_UDS::SendToHLE(u32 dest_node_id, u8 data_channel, u32 data_size, u8 flags,
                                std::vector<u8> input_buffer) {
    ASSERT(input_buffer.size() >= data_size);
    input_buffer.resize(data_size);

    std::scoped_lock lock(connection_status_mutex);
    if (connection_status.status != NetworkStatus::ConnectedAsClient &&
        connection_status.status != NetworkStatus::ConnectedAsHost) {
        LOG_ERROR(Service_NWM,
                  "You are not connected as a client or a host. (you are connected as type {})",
                  connection_status.status);
        return ResultStatus::SendError_NotConnected;
    }

    // There should never be a dest_node_id of 0
    if (dest_node_id == 0) {
        LOG_ERROR(Service_NWM, "dest_node_id is 0");
        return ResultStatus::SendError_BadNode;
    }

    if (dest_node_id == connection_status.network_node_id) {
        LOG_ERROR(Service_NWM, "tried to send packet to itself");
        return ResultStatus::SendError_BadNode;
    }

    if (flags >> 2) {
        LOG_ERROR(Service_NWM, "Unexpected flags 0x{:02X}", flags);
    }

    auto dest_address = GetNodeMacAddress(dest_node_id, flags);
    if (!dest_address) {
        LOG_ERROR(Service_NWM, "Destination address was 0");
        return ResultStatus::SendError_BadMacAddress;
    }

    constexpr std::size_t MaxSize = 0x5C6;
    if (data_size > MaxSize) {
        LOG_ERROR(Service_NWM, "Data size was greater than the max packet size {} > {}", data_size,
                  MaxSize);
        return ResultStatus::SendError_PacketSizeTooLarge;
    }
    // TODO(B3N30): Increment the sequence number after each sent packet.
    u16 sequence_number = 0;
    std::vector<u8> data_payload =
        GenerateDataPayload(input_buffer, data_channel, dest_node_id,
                            connection_status.network_node_id, sequence_number);

    // TODO(B3N30): Use the MAC address of the dest_node_id and our own to encrypt
    // and encapsulate the payload.

    Network::WifiPacket packet;

    packet.destination_address = *dest_address;
    packet.channel = network_channel;
    packet.data = std::move(data_payload);
    packet.type = Network::WifiPacket::PacketType::Data;

    SendPacket(packet);

    return ResultStatus::ResultSuccess;
}

void NWM_UDS::PullPacket(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    u32 bind_node_id = rp.Pop<u32>();
    u32 max_out_buff_size_aligned = rp.Pop<u32>();
    u32 max_out_buff_size = rp.Pop<u32>();

    std::vector<u8> output_buffer;
    SecureDataHeader secure_data{};

    auto ret = PullPacketHLE(bind_node_id,
                             max_out_buff_size,
                             max_out_buff_size_aligned,
                             output_buffer,
                             &secure_data);

    if (ret.has_value() && *ret > 0) {
        LOG_ERROR(Service_NWM,
                  "PullPacket RX size={} src_node={} bind={} buffer={}",
                  *ret,
                  static_cast<u32>(secure_data.src_node_id),
                  bind_node_id,
                  output_buffer.size());
    } else if (!ret.has_value()) {
        LOG_ERROR(Service_NWM,
                  "PullPacket FAILED bind={} error={}",
                  bind_node_id,
                  static_cast<u32>(ret.error()));
    }

    if (!ret.has_value()) {
        switch (ret.error()) {
        case ResultStatus::RecvError_NotConnected: {
            IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
            rb.Push(Result(ErrorDescription::NotAuthorized,
                           ErrorModule::UDS,
                           ErrorSummary::InvalidState,
                           ErrorLevel::Status));
            return;
        }

        case ResultStatus::RecvError_BadNode: {
            IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
            rb.Push(Result(ErrorDescription::NotAuthorized,
                           ErrorModule::UDS,
                           ErrorSummary::InvalidState,
                           ErrorLevel::Status));
            return;
        }

        case ResultStatus::RecvError_PacketSizeTooLarge: {
            IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);
            rb.Push(Result(ErrorDescription::TooLarge,
                           ErrorModule::UDS,
                           ErrorSummary::WrongArgument,
                           ErrorLevel::Usage));
            return;
        }

        default:
            break;
        }
    }

    IPC::RequestBuilder rb = rp.MakeBuilder(3, 2);

    rb.Push(ResultSuccess);
    rb.Push<u32>(ret.value_or(0));
    rb.Push<u16>(secure_data.src_node_id);

    rb.PushStaticBuffer(std::move(output_buffer), 0);
}

Common::Expected<int, ResultStatus> NWM_UDS::PullPacketHLE(
    u32 bind_node_id,
    u32 max_out_buff_size,
    u32 max_out_buff_size_aligned,
    std::vector<u8>& output_buffer,
    void* secure_data_out) {

    u32 buff_size = std::min<u32>(max_out_buff_size_aligned, 0x172) << 2;

    std::scoped_lock lock(connection_status_mutex);


    if (connection_status.status != NetworkStatus::ConnectedAsHost &&
        connection_status.status != NetworkStatus::ConnectedAsClient &&
        connection_status.status != NetworkStatus::ConnectedAsSpectator) {

        LOG_ERROR(Service_NWM,
                  "PullPacketHLE FAIL: not connected status={}",
                  static_cast<u32>(connection_status.status));

        return Common::Unexpected(ResultStatus::RecvError_NotConnected);
    }


    auto channel =
        std::find_if(channel_data.begin(),
                     channel_data.end(),
                     [bind_node_id](const auto& data) {
                         return data.second.bind_node_id == bind_node_id;
                     });


    if (channel == channel_data.end()) {

        LOG_ERROR(Service_NWM,
                  "PullPacketHLE FAIL: channel missing bind={}",
                  bind_node_id);

        return Common::Unexpected(ResultStatus::RecvError_BadNode);
    }


    // No packet available.
    // Do not log this. The game polls this constantly.
    if (channel->second.received_packets.empty()) {

        output_buffer.resize(buff_size);
        return int(0);
    }


    const auto& next_packet =
        channel->second.received_packets.front();


    auto secure_data = ParseSecureDataHeader(next_packet);
    auto data_size = secure_data.GetActualDataSize();


    LOG_ERROR(Service_NWM,
              "PullPacket RX src={} dst={} channel={} size={} bind={}",
              static_cast<u32>(secure_data.src_node_id),
              static_cast<u32>(secure_data.dest_node_id),
              static_cast<u32>(secure_data.data_channel),
              data_size,
              bind_node_id);


    if (secure_data_out) {
        *reinterpret_cast<SecureDataHeader*>(secure_data_out) = secure_data;
    }


    if (data_size > max_out_buff_size) {

        LOG_ERROR(Service_NWM,
                  "PullPacketHLE FAIL: packet too large size={} max={}",
                  data_size,
                  max_out_buff_size);

        return Common::Unexpected(ResultStatus::RecvError_PacketSizeTooLarge);
    }


    output_buffer.resize(buff_size);


    std::memcpy(output_buffer.data(),
                next_packet.data() + sizeof(LLCHeader) +
                    sizeof(SecureDataHeader),
                data_size);


    channel->second.received_packets.pop_front();


    LOG_ERROR(Service_NWM,
              "PullPacketHLE DONE remaining={}",
              channel->second.received_packets.size());


    return int(data_size);
}

void NWM_UDS::GetChannel(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    IPC::RequestBuilder rb = rp.MakeBuilder(2, 0);

    std::scoped_lock lock(connection_status_mutex);
    bool is_connected = connection_status.status != NetworkStatus::NotConnected;

    u8 channel = is_connected ? network_channel : 0;

    rb.Push(ResultSuccess);
    rb.Push(channel);

    LOG_DEBUG(Service_NWM, "called");
}

class NWM_UDS::ThreadCallback : public Kernel::HLERequestContext::WakeupCallback {
public:
    explicit ThreadCallback(u16 command_id_) : command_id(command_id_) {}

    void WakeUp(std::shared_ptr<Kernel::Thread> thread, Kernel::HLERequestContext& ctx,
                Kernel::ThreadWakeupReason reason) {
        IPC::RequestBuilder rb(ctx, command_id, 1, 0);
        if (reason == Kernel::ThreadWakeupReason::Timeout) {
            LOG_ERROR(Service_NWM, "timed out when trying to connect to UDS server");
            rb.Push(Result(ErrorDescription::Timeout, ErrorModule::UDS, ErrorSummary::Canceled,
                           ErrorLevel::Status));
            return;
        }
        rb.Push(ResultSuccess);
        LOG_DEBUG(Service_NWM, "connection sequence finished");
    }

private:
    ThreadCallback() = default;
    u16 command_id;

    template <class Archive>
    void serialize(Archive& ar, const unsigned int) {
        ar& boost::serialization::base_object<Kernel::HLERequestContext::WakeupCallback>(*this);
        ar & command_id;
    }
    friend class boost::serialization::access;
};

void NWM_UDS::ConnectToNetworkHLE(NetworkInfo net_info, u8 connection_type,
                                  std::vector<u8> passphrase) {
    network_info = net_info;

    conn_type = static_cast<ConnectionType>(connection_type);

    // Start the connection sequence
    StartConnectionSequence(network_info.host_mac_address);
}

void NWM_UDS::ConnectToNetwork(Kernel::HLERequestContext& ctx, u16 command_id,
                               std::span<const u8> network_info_buffer, u8 connection_type,
                               std::vector<u8> passphrase) {
    NetworkInfo net_info;
    std::memcpy(&net_info, network_info_buffer.data(), network_info_buffer.size());
    ConnectToNetworkHLE(net_info, connection_type, passphrase);
    // Originally 300 ms, but was changed to 5s to accommodate high ping
    // Since this timing is handled by core_timing it could differ from the 'real world' time
    static constexpr std::chrono::nanoseconds UDSConnectionTimeout{5000000000};

    connection_event = ctx.SleepClientThread("uds::ConnectToNetwork", UDSConnectionTimeout,
                                             std::make_shared<ThreadCallback>(command_id));
}

void NWM_UDS::ConnectToNetwork(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    const auto connection_type = rp.Pop<u8>();
    [[maybe_unused]] const auto passphrase_size = rp.Pop<u32>();

    const std::vector<u8> network_info_buffer = rp.PopStaticBuffer();
    ASSERT(network_info_buffer.size() == sizeof(NetworkInfo));

    std::vector<u8> passphrase = rp.PopStaticBuffer();

    ConnectToNetwork(ctx, 0x1E, network_info_buffer, connection_type, std::move(passphrase));

    LOG_DEBUG(Service_NWM, "called");
}

void NWM_UDS::ConnectToNetworkDeprecated(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    // Similar to BeginHostingNetworkDeprecated, we only read the first 0x3C bytes into the network
    // info
    const auto network_info_buffer = rp.PopRaw<std::array<u8, 0x3C>>();

    const auto connection_type = rp.Pop<u8>();
    [[maybe_unused]] const auto passphrase_size = rp.Pop<u32>();

    std::vector<u8> passphrase = rp.PopStaticBuffer();

    ConnectToNetwork(ctx, 0x09, network_info_buffer, connection_type, std::move(passphrase));

    LOG_DEBUG(Service_NWM, "called");
}

ResultStatus NWM_UDS::DisconnectNetworkHLE() {
    using Network::WifiPacket;

    LOG_ERROR(Service_NWM,
              "DisconnectNetworkHLE ENTER status={} node={} total_nodes={} channels={}",
              static_cast<u32>(connection_status.status),
              static_cast<u16>(connection_status.network_node_id),
              connection_status.total_nodes,
              channel_data.size());

    WifiPacket deauth;

    {
        std::scoped_lock lock(connection_status_mutex);

        LOG_ERROR(Service_NWM,
                  "DisconnectNetworkHLE LOCKED node_map={} lookup_ready={}",
                  node_map.size(),
                  std::count_if(node_lookup.begin(),
                                node_lookup.end(),
                                [](const auto& x) {
                                    return x != boost::none;
                                }));


        const u16_le node_id = connection_status.network_node_id;


        if (connection_status.status == NetworkStatus::ConnectedAsHost) {

            LOG_ERROR(Service_NWM,
                      "DisconnectNetworkHLE HOST RESET");


            connection_status = {};
            connection_status.status = NetworkStatus::ConnectedAsHost;
            connection_status.network_node_id = node_id;


            node_map.clear();
            node_lookup.fill(boost::none);


            LOG_ERROR(Service_NWM,
                      "DisconnectNetworkHLE HOST DONE");


            return ResultStatus::DisconError_CalledAsHost;
        }


        LOG_ERROR(Service_NWM,
                  "DisconnectNetworkHLE CLIENT RESET");


        /*
         * Keep current_node information.
         *
         * Do not destroy node_info because reconnect may immediately
         * receive secure packets from the previous session.
         */

        connection_status = {};

        connection_status.status = NetworkStatus::NotConnected;
        connection_status.network_node_id = node_id;

        connection_status.total_nodes = 0;
        connection_status.changed_nodes = 0;
        connection_status.node_bitmask = 0;


        node_map.clear();
        node_lookup.fill(boost::none);


        node_info.clear();
        node_info.push_back(current_node);


        connection_status_event->Signal();


        deauth.channel = network_channel;
        deauth.data = {};
        deauth.destination_address = network_info.host_mac_address;
        deauth.type = WifiPacket::PacketType::Deauthentication;


        LOG_ERROR(Service_NWM,
                  "DisconnectNetworkHLE prepared DEAUTH host={:02X}:{:02X}:{:02X}:{:02X}:{:02X}:{:02X}",
                  network_info.host_mac_address[0],
                  network_info.host_mac_address[1],
                  network_info.host_mac_address[2],
                  network_info.host_mac_address[3],
                  network_info.host_mac_address[4],
                  network_info.host_mac_address[5]);
    }


    LOG_ERROR(Service_NWM,
              "DisconnectNetworkHLE sending DEAUTH");


    SendPacket(deauth);


    LOG_ERROR(Service_NWM,
              "DisconnectNetworkHLE DEAUTH sent");


    {
        std::scoped_lock lock(connection_status_mutex);


        for (auto& [channel, data] : channel_data) {

            LOG_ERROR(Service_NWM,
                      "DisconnectNetworkHLE signaling channel={} bind={} node={}",
                      static_cast<u32>(channel),
                      data.bind_node_id,
                      data.network_node_id);


            data.event->Signal();
        }


        /*
         * Keep channel_data alive.
         *
         * Fast reconnect can reuse old channel.
         */

        LOG_ERROR(Service_NWM,
                  "DisconnectNetworkHLE KEEP channels={}",
                  channel_data.size());
    }


    LOG_ERROR(Service_NWM,
              "DisconnectNetworkHLE EXIT");


    return ResultStatus::ResultSuccess;
}

void NWM_UDS::DisconnectNetwork(Kernel::HLERequestContext& ctx) {

    LOG_ERROR(Service_NWM,
              "GAME CALLED DisconnectNetwork status={} node={} total_nodes={} node_map={}",
              static_cast<u32>(connection_status.status),
              connection_status.network_node_id,
              connection_status.total_nodes,
              node_map.size());

    IPC::RequestParser rp(ctx);
    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);

    auto res = DisconnectNetworkHLE();

    LOG_ERROR(Service_NWM,
              "DisconnectNetwork() returned res={}",
              static_cast<int>(res));

    if (res == ResultStatus::DisconError_CalledAsHost) {
        LOG_ERROR(Service_NWM,
                  "DisconnectNetwork() called as HOST");

        rb.Push(Result(ErrCodes::WrongStatus,
                       ErrorModule::UDS,
                       ErrorSummary::InvalidState,
                       ErrorLevel::Status));
        return;
    }

    LOG_ERROR(Service_NWM,
              "DisconnectNetwork() success");

    rb.Push(ResultSuccess);
}

void NWM_UDS::SetApplicationData(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    u32 size = rp.Pop<u32>();

    const std::vector<u8> application_data = rp.PopStaticBuffer();
    ASSERT(application_data.size() == size);

    LOG_DEBUG(Service_NWM, "called");

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);

    if (size > ApplicationDataSize) {
        rb.Push(Result(ErrorDescription::TooLarge, ErrorModule::UDS, ErrorSummary::WrongArgument,
                       ErrorLevel::Usage));
        return;
    }

    network_info.application_data_size = static_cast<u8>(size);
    std::memcpy(network_info.application_data.data(), application_data.data(), size);

    rb.Push(ResultSuccess);
}

void NWM_UDS::GetApplicationData(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);
    u32 input_size = rp.Pop<u32>();
    u8 appdata_size = network_info.application_data_size;

    IPC::RequestBuilder rb = rp.MakeBuilder(2, 2);
    rb.Push(ResultSuccess);

    if (input_size < appdata_size) {
        rb.Push(0);
        return;
    }

    rb.Push(appdata_size);
    std::vector<u8> appdata(appdata_size);
    std::memcpy(appdata.data(), network_info.application_data.data(), appdata_size);
    rb.PushStaticBuffer(std::move(appdata), 0);
}

void NWM_UDS::DecryptBeaconData(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    const std::vector<u8> network_struct_buffer = rp.PopStaticBuffer();
    ASSERT(network_struct_buffer.size() == sizeof(NetworkInfo));

    const std::vector<u8> encrypted_data0_buffer = rp.PopStaticBuffer();
    const std::vector<u8> encrypted_data1_buffer = rp.PopStaticBuffer();

    LOG_DEBUG(Service_NWM, "called");

    NetworkInfo net_info;
    std::memcpy(&net_info, network_struct_buffer.data(), sizeof(net_info));

    // Read the encrypted data.
    // The first 4 bytes should be the OUI and the OUI Type of the tags.
    std::array<u8, 3> oui;
    std::memcpy(oui.data(), encrypted_data0_buffer.data(), oui.size());
    ASSERT_MSG(oui == NintendoOUI, "Unexpected OUI");

    ASSERT_MSG(encrypted_data0_buffer[3] == static_cast<u8>(NintendoTagId::EncryptedData0),
               "Unexpected tag id");

    std::vector<u8> beacon_data(encrypted_data0_buffer.size() - 4 + encrypted_data1_buffer.size() -
                                4);
    std::memcpy(beacon_data.data(), encrypted_data0_buffer.data() + 4,
                encrypted_data0_buffer.size() - 4);
    std::memcpy(beacon_data.data() + encrypted_data0_buffer.size() - 4,
                encrypted_data1_buffer.data() + 4, encrypted_data1_buffer.size() - 4);

    // Decrypt the data
    DecryptBeacon(net_info, beacon_data);

    // The beacon data header contains the MD5 hash of the data.
    BeaconData beacon_header;
    std::memcpy(&beacon_header, beacon_data.data(), sizeof(beacon_header));

    // TODO(Subv): Verify the MD5 hash of the data and return 0xE1211005 if invalid.

    const std::size_t num_nodes = net_info.max_nodes;

    std::vector<NodeInfo> nodes;
    nodes.reserve(num_nodes);

    for (std::size_t i = 0; i < num_nodes; ++i) {
        BeaconNodeInfo info;
        std::memcpy(&info, beacon_data.data() + sizeof(beacon_header) + i * sizeof(info),
                    sizeof(info));

        // Deserialize the node information.
        auto& node = nodes.emplace_back();
        node.friend_code_seed = info.friend_code_seed;
        node.network_node_id = info.network_node_id;
        for (std::size_t j = 0; j < info.username.size(); ++j) {
            node.username[j] = info.username[j];
        }
    }

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 2);
    rb.Push(ResultSuccess);

    std::vector<u8> output_buffer(sizeof(NodeInfo) * UDSMaxNodes);
    std::memcpy(output_buffer.data(), nodes.data(), sizeof(NodeInfo) * nodes.size());
    rb.PushStaticBuffer(std::move(output_buffer), 0);
}

void NWM_UDS::EjectSpectators(Kernel::HLERequestContext& ctx) {
    IPC::RequestParser rp(ctx);

    LOG_WARNING(Service_NWM, "(STUBBED) called");

    IPC::RequestBuilder rb = rp.MakeBuilder(1, 0);

    rb.Push(ResultSuccess);
}

// Sends a 802.11 beacon frame with information about the current network.
void NWM_UDS::BeaconBroadcastCallback(std::uintptr_t user_data, s64 cycles_late) {
    // Don't do anything if we're not actually hosting a network
    if (connection_status.status != NetworkStatus::ConnectedAsHost)
        return;

    std::vector<u8> frame = GenerateBeaconFrame(network_info, node_info);

    using Network::WifiPacket;
    WifiPacket packet;
    packet.type = WifiPacket::PacketType::Beacon;
    packet.data = std::move(frame);
    packet.destination_address = Network::BroadcastMac;
    packet.channel = network_channel;

    SendPacket(packet);

    // Start broadcasting the network, send a beacon frame every 102.4ms.
    system.CoreTiming().ScheduleEvent(msToCycles(DefaultBeaconInterval * MillisecondsPerTU) -
                                          cycles_late,
                                      beacon_broadcast_event, 0);
}

Network::MacAddress NWM_UDS::GetMacAddress() {
    MacAddress mac;

    if (auto room_member = Network::GetRoomMember().lock();
        room_member && room_member->IsConnected()) {
        mac = room_member->GetMacAddress();
        if (mac != CFG::GetConsoleMacAddress(system)) {
            LOG_WARNING(Service_NWM, "Room member mac address is different from the console mac "
                                     "address. Using room member mac address.");
        }
    } else {
        // if we are not connected to the room, we can
        // use the system mac address. In hopefully all cases
        // this will match the room member mac addr anyways
        mac = CFG::GetConsoleMacAddress(system);
    }
    return mac;
}

NWM_UDS::NWM_UDS(Core::System& system) : ServiceFramework("nwm::UDS"), system(system) {
    static const FunctionInfo functions[] = {
        // clang-format off
        {0x0001, &NWM_UDS::InitializeDeprecated, "Initialize (deprecated)"},
        {0x0002, nullptr, "Scrap"},
        {0x0003, &NWM_UDS::Shutdown, "Shutdown"},
        {0x0004, &NWM_UDS::BeginHostingNetworkDeprecated, "BeginHostingNetwork (deprecated)"},
        {0x0005, &NWM_UDS::EjectClient, "EjectClient"},
        {0x0006, &NWM_UDS::EjectSpectators, "EjectSpectators"},
        {0x0007, &NWM_UDS::UpdateNetworkAttribute, "UpdateNetworkAttribute"},
        {0x0008, &NWM_UDS::DestroyNetwork, "DestroyNetwork"},
        {0x0009, &NWM_UDS::ConnectToNetworkDeprecated, "ConnectToNetwork (deprecated)"},
        {0x000A, &NWM_UDS::DisconnectNetwork, "DisconnectNetwork"},
        {0x000B, &NWM_UDS::GetConnectionStatus, "GetConnectionStatus"},
        {0x000D, &NWM_UDS::GetNodeInformation, "GetNodeInformation"},
        {0x000E, &NWM_UDS::DecryptBeaconData, "DecryptBeaconData (deprecated)"},
        {0x000F, &NWM_UDS::RecvBeaconBroadcastData, "RecvBeaconBroadcastData"},
        {0x0010, &NWM_UDS::SetApplicationData, "SetApplicationData"},
        {0x0011, &NWM_UDS::GetApplicationData, "GetApplicationData"},
        {0x0012, &NWM_UDS::Bind, "Bind"},
        {0x0013, &NWM_UDS::Unbind, "Unbind"},
        {0x0014, &NWM_UDS::PullPacket, "PullPacket"},
        {0x0015, nullptr, "SetMaxSendDelay"},
        {0x0017, &NWM_UDS::SendTo, "SendTo"},
        {0x001A, &NWM_UDS::GetChannel, "GetChannel"},
        {0x001B, &NWM_UDS::InitializeWithVersion, "InitializeWithVersion"},
        {0x001D, &NWM_UDS::BeginHostingNetwork, "BeginHostingNetwork"},
        {0x001E, &NWM_UDS::ConnectToNetwork, "ConnectToNetwork"},
        {0x001F, &NWM_UDS::DecryptBeaconData, "DecryptBeaconData"},
        {0x0020, nullptr, "Flush"},
        {0x0021, nullptr, "SetProbeResponseParam"},
        {0x0022, nullptr, "ScanOnConnection"},
        // clang-format on
    };
    connection_status_event =
        system.Kernel().CreateEvent(Kernel::ResetType::OneShot, "NWM::connection_status_event");

    RegisterHandlers(functions);

    beacon_broadcast_event = system.CoreTiming().RegisterEvent(
        "UDS::BeaconBroadcastCallback", [this](std::uintptr_t user_data, s64 cycles_late) {
            BeaconBroadcastCallback(user_data, cycles_late);
        });

    system.Kernel().GetSharedPageHandler().SetMacAddress(GetMacAddress());

    if (auto room_member = Network::GetRoomMember().lock()) {
        wifi_packet_received = room_member->BindOnWifiPacketReceived(
            [this](const Network::WifiPacket& packet) { OnWifiPacketReceived(packet); });
    } else {
        LOG_ERROR(Service_NWM, "Network isn't initalized");
    }
}

NWM_UDS::~NWM_UDS() {
    if (auto room_member = Network::GetRoomMember().lock())
        room_member->Unbind(wifi_packet_received);

    system.CoreTiming().UnscheduleEvent(beacon_broadcast_event, 0);
}

} // namespace Service::NWM

SERIALIZE_EXPORT_IMPL(Service::NWM::NWM_UDS::ThreadCallback)
