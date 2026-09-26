# How to use the salon app on your phone

Simple steps. Do them once and the app keeps working.

This app is **offline**. It keeps all its data inside the phone. No computer has
to be switched on, and the app never sends anything to the internet.

## 1. Put the app on the phone

**Android**

1. Send `E:\Barbar-Shop\Salon-App-v1.0.0.apk` to the phone (USB cable, or your own
   cloud storage).
2. Tap the file on the phone.
3. Android says the file came from an unknown source. Tap **Settings**, allow
   **Install unknown apps** for the app you are using (Files or Chrome).
4. Tap **Install**, then **Open**. If Android offers **Update**, accept it.

Never delete `android\release-signing.properties` or the keystore on your
computer. The same key is needed for every future update of this app.

**iPhone**

1. Open <https://slowyy0477.github.io/barbar_shop/> in Safari.
2. Tap **Share**, then **Add to Home Screen**.
3. Open it from the home screen. It looks and works like a normal app.

The phone does not need internet for day-to-day use. Opening the iPhone link the
first time does need internet, and after that it keeps working from the home
screen icon.

## 2. Customer sign up and sign in

There is no SMS code anywhere in this app. A mobile number and a password are all
a customer needs.

**New customer**

1. Tap **Sign in**, then **Create new account**.
2. Type the name, the mobile number, the password, then the password again.
3. Tick the box that allows promotional messages (optional).
4. Tap **Create account**. The customer is signed in straight away.

Password rule, tell the customer this:

- at least 10 characters and at most 20
- must have at least one letter and at least one number
- example: `SalonPass2026`

Tell the customer to remember the password. Nothing can be sent to the phone to
recover it.

**Coming back later**

1. Tap **Sign in**.
2. Type the same mobile number and password.
3. Tap **Sign in**. The phone remembers them, so normally they do not type it
   again.

**Customer forgot the password?** You fix it as the owner: open
**Owner > Customers**, find the customer, tap **Reset password**, set a new one,
then tell the customer the new password. They can change it later from **More**.

## 3. Owner menu (customers never see it)

The owner menu is hidden on purpose:

1. Tap the round salon logo at the top of the app 5 times quickly.
2. Tap **Owner**.
3. Type the owner access code and tap **Sign in**.

The first code is **530146**. Change it on your first day: the app was built with
that code, so anyone who reads these files knows it. Change it in
**Owner > Settings**.

Five wrong tries pause owner sign in for 15 minutes. Customers can never open
this menu.

## 4. Set the salon up once

1. **Owner > Settings** - salon name, tagline, phone, address, opening hours, and
   the phone app name and logo.
2. **Owner > Services & staff** - your services with prices and durations, your
   barbers, and the haircut styles with photos.
3. **Owner > Settings** - wallet top-up amount, first deposit bonus, expiry days,
   reminder cycle and referral reward.

Every amount is in **PKR** and can be changed whenever you like. Changing a price
does not change any old bill.

## 5. Keep a copy of your data

All the data lives on that one phone. If the phone is lost, reset or the app is
uninstalled, the data goes with it.

Use **Owner > Settings > Export workspace data** at the end of each week and keep
that file somewhere safe.

## 6. If something does not work

1. Close the app completely and open it again.
2. Check the phone has free storage space.
3. Still stuck? Keep your exported copy safe, then reinstall the APK.

## 7. Keep private

- The owner access code.
- Customer names, mobile numbers and wallet history.
- The export files, and the release keystore with
  `android\release-signing.properties`.
