use jni::objects::{GlobalRef, JClass, JObject, JString, JValue};
use jni::sys::{jint, jstring};
use jni::{JNIEnv, JavaVM};

use openvpn_connect::{
    Client, Config, DnsOptions, Event, EventHandler, TunBuilder,
};

use std::sync::{Arc, Mutex, OnceLock};
use std::thread;

static CLIENT: OnceLock<Mutex<Option<Client>>> = OnceLock::new();
static STATUS: OnceLock<Mutex<String>> = OnceLock::new();

fn client_store() -> &'static Mutex<Option<Client>> {
    CLIENT.get_or_init(|| Mutex::new(None))
}

fn set_status(value: impl Into<String>) {
    if let Ok(mut status) = status_store().lock() {
        *status = value.into();
    }
}

fn status_store() -> &'static Mutex<String> {
    STATUS.get_or_init(|| Mutex::new("Disconnected".into()))
}

#[derive(Clone)]
struct AndroidCallbacks {
    vm: Arc<JavaVM>,
    service: GlobalRef,
}

impl AndroidCallbacks {
    fn call_bool(&self, name: &str, sig: &str, args: &[JValue]) -> bool {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return false;
        };

        env.call_method(self.service.as_obj(), name, sig, args)
            .and_then(|v| v.z())
            .unwrap_or(false)
    }

    fn call_int(&self, name: &str, sig: &str, args: &[JValue]) -> i32 {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return -1;
        };

        env.call_method(self.service.as_obj(), name, sig, args)
            .and_then(|v| v.i())
            .unwrap_or(-1)
    }

    fn call_void(&self, name: &str, sig: &str, args: &[JValue]) {
        if let Ok(mut env) = self.vm.attach_current_thread() {
            let _ = env.call_method(self.service.as_obj(), name, sig, args);
        }
    }

    fn call_string_bool(&self, name: &str, value: &str) -> bool {
        let Ok(mut env) = self.vm.attach_current_thread() else {
            return false;
        };

        let Ok(value) = env.new_string(value) else {
            return false;
        };

        let object = JObject::from(value);

        env.call_method(
            self.service.as_obj(),
            name,
            "(Ljava/lang/String;)Z",
            &[JValue::Object(&object)],
        )
        .and_then(|v| v.z())
        .unwrap_or(false)
    }
}

struct AndroidTunnel {
    callbacks: AndroidCallbacks,
}

impl TunBuilder for AndroidTunnel {
    fn new_tunnel(&self) -> bool {
        self.callbacks.call_bool("nativeNewTunnel", "()Z", &[])
    }

    fn set_layer(&self, layer: i32) -> bool {
        self.callbacks.call_bool(
            "nativeSetLayer",
            "(I)Z",
            &[JValue::Int(layer)],
        )
    }

    fn set_remote_address(&self, address: &str, ipv6: bool) -> bool {
        let Ok(mut env) = self.callbacks.vm.attach_current_thread() else {
            return false;
        };

        let Ok(address) = env.new_string(address) else {
            return false;
        };

        let object = JObject::from(address);

        env.call_method(
            self.callbacks.service.as_obj(),
            "nativeSetRemoteAddress",
            "(Ljava/lang/String;Z)Z",
            &[
                JValue::Object(&object),
                JValue::Bool(ipv6 as u8),
            ],
        )
        .and_then(|v| v.z())
        .unwrap_or(false)
    }

    fn add_address(
        &self,
        address: &str,
        prefix: i32,
        gateway: &str,
        ipv6: bool,
        net30: bool,
    ) -> bool {
        let Ok(mut env) = self.callbacks.vm.attach_current_thread() else {
            return false;
        };

        let Ok(address) = env.new_string(address) else {
            return false;
        };

        let Ok(gateway) = env.new_string(gateway) else {
            return false;
        };

        let address = JObject::from(address);
        let gateway = JObject::from(gateway);

        env.call_method(
            self.callbacks.service.as_obj(),
            "nativeAddAddress",
            "(Ljava/lang/String;ILjava/lang/String;ZZ)Z",
            &[
                JValue::Object(&address),
                JValue::Int(prefix),
                JValue::Object(&gateway),
                JValue::Bool(ipv6 as u8),
                JValue::Bool(net30 as u8),
            ],
        )
        .and_then(|v| v.z())
        .unwrap_or(false)
    }

    fn set_route_metric_default(&self, metric: i32) -> bool {
        self.callbacks.call_bool(
            "nativeSetRouteMetricDefault",
            "(I)Z",
            &[JValue::Int(metric)],
        )
    }

    fn reroute_gateway(&self, ipv4: bool, ipv6: bool, flags: u32) -> bool {
        self.callbacks.call_bool(
            "nativeRerouteGateway",
            "(ZZI)Z",
            &[
                JValue::Bool(ipv4 as u8),
                JValue::Bool(ipv6 as u8),
                JValue::Int(flags as i32),
            ],
        )
    }

    fn add_route(
        &self,
        address: &str,
        prefix: i32,
        metric: i32,
        ipv6: bool,
    ) -> bool {
        let Ok(mut env) = self.callbacks.vm.attach_current_thread() else {
            return false;
        };

        let Ok(address) = env.new_string(address) else {
            return false;
        };

        let address = JObject::from(address);

        env.call_method(
            self.callbacks.service.as_obj(),
            "nativeAddRoute",
            "(Ljava/lang/String;IIZ)Z",
            &[
                JValue::Object(&address),
                JValue::Int(prefix),
                JValue::Int(metric),
                JValue::Bool(ipv6 as u8),
            ],
        )
        .and_then(|v| v.z())
        .unwrap_or(false)
    }

    fn exclude_route(
        &self,
        address: &str,
        prefix: i32,
        metric: i32,
        ipv6: bool,
    ) -> bool {
        let Ok(mut env) = self.callbacks.vm.attach_current_thread() else {
            return false;
        };

        let Ok(address) = env.new_string(address) else {
            return false;
        };

        let address = JObject::from(address);

        env.call_method(
            self.callbacks.service.as_obj(),
            "nativeExcludeRoute",
            "(Ljava/lang/String;IIZ)Z",
            &[
                JValue::Object(&address),
                JValue::Int(prefix),
                JValue::Int(metric),
                JValue::Bool(ipv6 as u8),
            ],
        )
        .and_then(|v| v.z())
        .unwrap_or(false)
    }

    fn set_dns_options(&self, dns: &DnsOptions) -> bool {
        let mut success = true;

        for server in &dns.servers {
            for address in &server.addresses {
                success &= self.callbacks.call_string_bool(
                    "nativeAddDns",
                    &address.address,
                );
            }
        }

        success
    }

    fn set_mtu(&self, mtu: i32) -> bool {
        self.callbacks.call_bool(
            "nativeSetMtu",
            "(I)Z",
            &[JValue::Int(mtu)],
        )
    }

    fn set_session_name(&self, name: &str) -> bool {
        self.callbacks.call_string_bool("nativeSetSession", name)
    }

    fn establish(&self) -> i32 {
        self.callbacks.call_int("nativeEstablish", "()I", &[])
    }

    fn persist(&self) -> bool {
        true
    }

    fn teardown(&self, disconnect: bool) {
        self.callbacks.call_void(
            "nativeTeardown",
            "(Z)V",
            &[JValue::Bool(disconnect as u8)],
        );
    }
}

impl EventHandler for AndroidTunnelHandler {
    fn event(&self, event: Event) {
        set_status(format!("{}: {}", event.name, event.info));
    }

    fn log(&self, text: &str) {
        set_status(text.to_string());
    }

    fn socket_protect(&self, socket: isize, _remote: &str, _ipv6: bool) -> bool {
        self.callbacks.call_bool(
            "nativeProtectSocket",
            "(I)Z",
            &[JValue::Int(socket as i32)],
        )
    }

    fn tun_builder(&self) -> Option<&dyn TunBuilder> {
        Some(&self.tunnel)
    }
}

struct AndroidTunnelHandler {
    callbacks: AndroidCallbacks,
    tunnel: AndroidTunnel,
}

#[no_mangle]
pub extern "system" fn Java_com_example_autoiprotator_OpenVpn3Bridge_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    config: JString,
    service: JObject,
) -> jint {
    let profile: String = match env.get_string(&config) {
        Ok(value) => value.into(),
        Err(_) => {
            set_status("Cannot read OpenVPN profile");
            return -1;
        }
    };

    if profile.trim().is_empty() {
        set_status("OpenVPN profile is empty");
        return -2;
    }

    let vm = match env.get_java_vm() {
        Ok(value) => value,
        Err(_) => return -3,
    };

    let global_service = match env.new_global_ref(service) {
        Ok(value) => value,
        Err(_) => return -4,
    };

    let callbacks = AndroidCallbacks {
        vm: Arc::new(vm),
        service: global_service,
    };

    let tunnel = AndroidTunnel {
        callbacks: callbacks.clone(),
    };

    let handler = AndroidTunnelHandler {
        callbacks,
        tunnel,
    };

    let client = match Client::new(handler) {
        Ok(value) => value,
        Err(error) => {
            set_status(error.to_string());
            return -5;
        }
    };

    let configuration = Config::new(profile);

    let evaluation = match client.evaluate(&configuration) {
        Ok(value) => value,
        Err(error) => {
            set_status(error.to_string());
            return -6;
        }
    };

    if !evaluation.autologin {
        set_status("This profile requires username/password credentials");
        return -7;
    }

    let control = client.clone();

    {
        let Ok(mut stored) = client_store().lock() else {
            return -8;
        };

        if let Some(previous) = stored.take() {
            previous.stop();
        }

        *stored = Some(control);
    }

    set_status("Connecting...");

    thread::spawn(move || {
        match client.connect() {
            Ok(result) => {
                set_status(format!("{}: {}", result.status, result.message));
            }
            Err(error) => {
                set_status(format!("Connection error: {}", error));
            }
        }
    });

    0
}

#[no_mangle]
pub extern "system" fn Java_com_example_autoiprotator_OpenVpn3Bridge_nativeStop(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    if let Ok(mut stored) = client_store().lock() {
        if let Some(client) = stored.take() {
            client.stop();
        }
    }

    set_status("Disconnected");
    0
}

#[no_mangle]
pub extern "system" fn Java_com_example_autoiprotator_OpenVpn3Bridge_nativeGetStatus(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let message = status_store()
        .lock()
        .map(|value| value.clone())
        .unwrap_or_else(|_| "Status unavailable".into());

    match env.new_string(message) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}