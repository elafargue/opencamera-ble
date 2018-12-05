# opencamera-ble
Adding remote BLE control for the awesome OpenCamera app.

This is a fork of Open Camera, that adds support for Bluetooth LE remote controls. This is a generic implementation
that can be adapted to potentially any kind of remote control. So far, the Kraken Smart Housing is the first remote
control type that is supported.

Features:

- Autoconnect when "remote control" is enabled
- Shutter/Take picture
- Toggle Photo/Video
- Select camera properties (Autofocus, flash, etc) using remote "AF-MF/Up/Down/Back" buttons
- Adjust ISO settings using remote "Menu/Up/Down/Back" buttons

Note: Open Camera tends to display various info screens at first use (for HDR for instance). Make sure you clear them before
putting Open Camera in remote control, otherwise those will remain on the screen. A future update will detect this and avoid
displaying those info dialogs when a remote is connected.

Contact me if you would like to add support for other types of remote controls for Open Camera.

Last: I am hoping this code can eventually make its way into the core Open Camera codebase, of course.
