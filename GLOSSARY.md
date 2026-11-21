# Bitcoin Alert

A personal app that watches the BTC/USDT price and tells its user when the price crosses targets they set.

## Language

### Alerts

**Alert**:
A user-defined rule that fires once when the price crosses a target in a given direction.
_Avoid_: Alarm (for the rule itself), trigger, watch

**Delivery**:
How a fired Alert reaches the user: as a Notification or as an Alarm. Chosen when the Alert is created and fixed for its lifetime.
_Avoid_: Mode, type, channel

**Notification**:
A Delivery that sounds once and stays in the notification tray.
_Avoid_: Push, message

**Alarm**:
A Delivery that keeps ringing until the user dismisses it, meant to wake the user.
_Avoid_: Loud notification, wake-up alert
