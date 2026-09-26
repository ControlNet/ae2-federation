"""Send real XTEST keyboard input to the isolated Minecraft client (DISPLAY required)."""
import sys
import time
from Xlib import X, XK, display
from Xlib.ext import xtest

d = display.Display()
def key(name, down=True):
    xtest.fake_input(d, X.KeyPress if down else X.KeyRelease, d.keysym_to_keycode(XK.string_to_keysym(name)))
    d.sync()
def tap(name):
    key(name); time.sleep(.08); key(name,False)
if sys.argv[1] == 'shot':
    from pathlib import Path
    import shutil
    source=Path(sys.argv[2]);target=Path(sys.argv[3])
    time.sleep(1)  # Let teleports and item equip animations settle before F2.
    before=set(source.glob('*.png'))
    tap('F2')
    for _ in range(100):
        time.sleep(.1)
        new=set(source.glob('*.png'))-before
        if new:
            time.sleep(.2);target.parent.mkdir(parents=True,exist_ok=True)
            shutil.copy2(max(new,key=lambda p:p.stat().st_mtime),target)
            print(target);break
    else: raise RuntimeError('Minecraft did not save a screenshot')
elif sys.argv[1] == 'close':
    from Xlib.protocol import event
    focus=d.get_input_focus().focus
    focus.send_event(event.ClientMessage(window=focus,client_type=d.intern_atom('WM_PROTOCOLS'),data=(32,[d.intern_atom('WM_DELETE_WINDOW'),X.CurrentTime,0,0,0])))
    d.sync()
elif sys.argv[1] == 'rightclick':
    xtest.fake_input(d,X.ButtonPress,3);d.sync();time.sleep(.08)
    xtest.fake_input(d,X.ButtonRelease,3);d.sync()
elif sys.argv[1] == 'reload':
    key('F3'); tap('t'); key('F3',False)
elif sys.argv[1] == 'profile':
    key('F3'); tap('l'); key('F3',False)
else:
    tap(sys.argv[1])
