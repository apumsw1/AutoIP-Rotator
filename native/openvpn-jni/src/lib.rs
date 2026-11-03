
use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;

const ERR_NOT_IMPLEMENTED: jint = -10;

const STATUS: &str =
    "OpenVPN engine is not integrated yet. No VPN connection is active.";

#[no_mangle]
pub extern "system" fn Java_com_example_autoiprotator_OpenVpn3Bridge_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    config: JString,
    tun_fd: jint,
) -> jint {

    let profile: String = match env.get_string(&config) {
        Ok(value) => value.into(),
        Err(_) => return -1,
    };

    if profile.trim().is_empty() || tun_fd < 0 {
        return -2;
    }

    // OpenVPN session and Android TUN integration
    // are not implemented yet.
    ERR_NOT_IMPLEMENTED
}

#[no_mangle]
pub extern "system" fn Java_com_example_autoiprotator_OpenVpn3Bridge_nativeStop(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    ERR_NOT_IMPLEMENTED
}

#[no_mangle]
pub extern "system" fn Java_com_example_autoiprotator_OpenVpn3Bridge_nativeGetStatus(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    match env.new_string(STATUS) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
