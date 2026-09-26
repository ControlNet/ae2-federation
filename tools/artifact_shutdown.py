import ctypes
import os
import socket
import struct
from pathlib import Path


class ClientMessage(ctypes.Structure):
    _fields_ = [("type", ctypes.c_int), ("serial", ctypes.c_ulong),
                ("send_event", ctypes.c_int), ("display", ctypes.c_void_p),
                ("window", ctypes.c_ulong), ("message_type", ctypes.c_ulong),
                ("format", ctypes.c_int), ("data", ctypes.c_long * 5)]


def close_client_window(descendants: list[int]) -> None:
    display_name = None
    authority = None
    for pid in descendants:
        try:
            command = (Path("/proc") / str(pid) / "cmdline").read_bytes()
            if b"java" not in command or b"net.minecraft" not in command:
                continue
            environment = (Path("/proc") / str(pid) / "environ").read_bytes().split(b"\0")
        except (FileNotFoundError, PermissionError):
            continue
        variables = dict(entry.split(b"=", 1) for entry in environment if b"=" in entry)
        display_name = variables.get(b"DISPLAY")
        authority = variables.get(b"XAUTHORITY")
        if display_name and authority:
            break
    if not display_name or not authority:
        raise ValueError("Artifact client X display was not found in its game process")

    x11 = ctypes.CDLL("libX11.so.6")
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XDefaultRootWindow.argtypes = [ctypes.c_void_p]
    x11.XDefaultRootWindow.restype = ctypes.c_ulong
    x11.XInternAtom.argtypes = [ctypes.c_void_p, ctypes.c_char_p, ctypes.c_int]
    x11.XInternAtom.restype = ctypes.c_ulong
    x11.XQueryTree.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.POINTER(ctypes.c_ulong),
                              ctypes.POINTER(ctypes.c_ulong), ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)),
                              ctypes.POINTER(ctypes.c_uint)]
    x11.XQueryTree.restype = ctypes.c_int
    x11.XGetWMProtocols.argtypes = [ctypes.c_void_p, ctypes.c_ulong,
                                   ctypes.POINTER(ctypes.POINTER(ctypes.c_ulong)), ctypes.POINTER(ctypes.c_int)]
    x11.XGetWMProtocols.restype = ctypes.c_int
    x11.XSendEvent.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_long, ctypes.c_void_p]
    x11.XSendEvent.restype = ctypes.c_int
    x11.XFree.argtypes = [ctypes.c_void_p]
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    x11.XFlush.argtypes = [ctypes.c_void_p]
    prior_authority = os.environ.get("XAUTHORITY")
    os.environ["XAUTHORITY"] = os.fsdecode(authority)
    try:
        display = x11.XOpenDisplay(display_name)
    finally:
        if prior_authority is None:
            os.environ.pop("XAUTHORITY", None)
        else:
            os.environ["XAUTHORITY"] = prior_authority
    if not display:
        raise ValueError("Artifact client X display could not be opened")
    try:
        wm_protocols = x11.XInternAtom(display, b"WM_PROTOCOLS", 0)
        wm_delete = x11.XInternAtom(display, b"WM_DELETE_WINDOW", 0)
        parent = ctypes.c_ulong()
        root = ctypes.c_ulong()
        children = ctypes.POINTER(ctypes.c_ulong)()
        count = ctypes.c_uint()
        if not x11.XQueryTree(display, x11.XDefaultRootWindow(display), ctypes.byref(root),
                             ctypes.byref(parent), ctypes.byref(children), ctypes.byref(count)):
            raise ValueError("Artifact X window tree could not be read")
        try:
            windows = [children[index] for index in range(count.value)]
        finally:
            if children:
                x11.XFree(children)
        for window in windows:
            protocols = ctypes.POINTER(ctypes.c_ulong)()
            length = ctypes.c_int()
            if not x11.XGetWMProtocols(display, window, ctypes.byref(protocols), ctypes.byref(length)):
                continue
            try:
                supports_close = wm_delete in (protocols[index] for index in range(length.value))
            finally:
                if protocols:
                    x11.XFree(protocols)
            if supports_close:
                message = ClientMessage(33, 0, 1, display, window, wm_protocols, 32,
                                        (ctypes.c_long * 5)(wm_delete, 0, 0, 0, 0))
                if not x11.XSendEvent(display, window, 0, 0, ctypes.byref(message)):
                    raise ValueError("Artifact client window refused WM_DELETE_WINDOW")
                x11.XFlush(display)
                return
        raise ValueError("Artifact client window did not advertise WM_DELETE_WINDOW")
    finally:
        x11.XCloseDisplay(display)


def stop_server(port: int, password: str) -> None:
    def packet(request_id: int, kind: int, text: str) -> bytes:
        payload = struct.pack("<ii", request_id, kind) + text.encode() + b"\0\0"
        return struct.pack("<i", len(payload)) + payload

    def response(connection: socket.socket) -> tuple[int, int]:
        size = struct.unpack("<i", connection.recv(4, socket.MSG_WAITALL))[0]
        if size < 10 or size > 4096:
            raise ValueError("Invalid task-owned RCON response length")
        body = connection.recv(size, socket.MSG_WAITALL)
        if len(body) != size:
            raise ValueError("Truncated task-owned RCON response")
        return struct.unpack("<ii", body[:8])

    with socket.create_connection(("127.0.0.1", port), timeout=10) as connection:
        connection.settimeout(10)
        connection.sendall(packet(39, 3, password))
        request_id, _ = response(connection)
        if request_id != 39:
            raise ValueError("Task-owned RCON authentication failed")
        connection.sendall(packet(40, 2, "stop"))
