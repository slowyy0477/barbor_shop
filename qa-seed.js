// Browser-only QA fixtures. This file is intentionally excluded from the Android
// asset bundle so a distributed APK contains no sample customer or money records.
(function (global) {
  global.AYAN_QA_SEED = function (base, helpers) {
    const baseToday = helpers.baseToday;
    const addDays = helpers.addDays;
    const now = helpers.now;
    return {
      ...base,
      schemaVersion: 4,
      releaseMode: false,
      customers: [
        { id: "cus_001", name: "Ali Raza", phone: "0300 111 2233", consent: true, createdAt: addDays(baseToday, -180), lastVisit: addDays(baseToday, -25), paidCredit: 850, bonusCredit: 100, birthday: "1995-03-18" },
        { id: "cus_002", name: "Usman Tariq", phone: "0301 222 3344", consent: true, createdAt: addDays(baseToday, -140), lastVisit: addDays(baseToday, -17), paidCredit: 0, bonusCredit: 0, birthday: "1992-08-07" },
        { id: "cus_003", name: "Hira Ahmed", phone: "0322 333 4455", consent: true, createdAt: addDays(baseToday, -100), lastVisit: addDays(baseToday, -42), paidCredit: 500, bonusCredit: 50, birthday: "1999-11-22" },
        { id: "cus_004", name: "Bilal Shah", phone: "0333 444 5566", consent: false, createdAt: addDays(baseToday, -80), lastVisit: addDays(baseToday, -8), paidCredit: 0, bonusCredit: 0, birthday: "1988-02-12" },
        { id: "cus_005", name: "Maham Noor", phone: "0345 555 6677", consent: true, createdAt: addDays(baseToday, -60), lastVisit: addDays(baseToday, -31), paidCredit: 0, bonusCredit: 0, birthday: "1997-07-03" },
        { id: "cus_006", name: "Danish Malik", phone: "0306 666 7788", consent: true, createdAt: addDays(baseToday, -48), lastVisit: addDays(baseToday, -15), paidCredit: 1200, bonusCredit: 100, birthday: "1991-10-27" },
        { id: "cus_007", name: "Zainab Fatima", phone: "0312 777 8899", consent: true, createdAt: addDays(baseToday, -37), lastVisit: addDays(baseToday, -21), paidCredit: 0, bonusCredit: 0, birthday: "2000-04-09" },
        { id: "cus_008", name: "Shoaib Ahmed", phone: "0315 888 9900", consent: false, createdAt: addDays(baseToday, -25), lastVisit: addDays(baseToday, -10), paidCredit: 500, bonusCredit: 50, birthday: "1990-01-15" },
        { id: "cus_009", name: "Areeba Khan", phone: "0334 999 0011", consent: true, createdAt: addDays(baseToday, -17), lastVisit: null, paidCredit: 0, bonusCredit: 0, birthday: "2002-09-14" },
        { id: "cus_010", name: "Faizan Ali", phone: "0309 123 4567", consent: true, createdAt: addDays(baseToday, -4), lastVisit: addDays(baseToday, -2), paidCredit: 0, bonusCredit: 0, birthday: "1996-12-01" }
      ],
      bookings: [
        { id: "bk_001", customerId: "cus_001", serviceId: "svc_haircut", addonIds: ["addon_beard"], staffId: "st_adeel", date: baseToday, time: "17:30", status: "Confirmed", total: 899, duration: 47, source: "Reminder", paymentMethod: "Pay at salon", createdAt: addDays(baseToday, -2) },
        { id: "bk_002", customerId: "cus_002", serviceId: "svc_combo", addonIds: [], staffId: "st_hamza", date: baseToday, time: "18:00", status: "Completed", total: 1100, duration: 50, source: "Direct", paymentMethod: "Cash", createdAt: addDays(baseToday, -1) },
        { id: "bk_003", customerId: "cus_003", serviceId: "svc_facial", addonIds: ["addon_massage"], staffId: "st_sana", date: addDays(baseToday, 1), time: "13:00", status: "Confirmed", total: 1699, duration: 73, source: "Direct", paymentMethod: "Pay at salon", createdAt: baseToday },
        { id: "bk_004", customerId: "cus_004", serviceId: "svc_beard", addonIds: [], staffId: "st_adeel", date: addDays(baseToday, 1), time: "15:00", status: "Pending", total: 450, duration: 20, source: "Walk-in", paymentMethod: "Pay at salon", createdAt: baseToday },
        { id: "bk_005", customerId: "cus_005", serviceId: "svc_haircut", addonIds: [], staffId: "st_hamza", date: addDays(baseToday, -1), time: "16:00", status: "No-show", total: 800, duration: 35, source: "Direct", paymentMethod: "Pay at salon", createdAt: addDays(baseToday, -3) }
      ],
      visits: [
        { id: "visit_001", bookingId: "bk_002", customerId: "cus_002", serviceId: "svc_combo", staffId: "st_hamza", date: addDays(baseToday, -1), amount: 1100, paymentStatus: "Paid", addonIds: [], completedAt: addDays(baseToday, -1) },
        { id: "visit_002", bookingId: "bk_006", customerId: "cus_001", serviceId: "svc_haircut", staffId: "st_adeel", date: addDays(baseToday, -25), amount: 899, paymentStatus: "Paid", addonIds: ["addon_beard"], completedAt: addDays(baseToday, -25) },
        { id: "visit_003", bookingId: "bk_007", customerId: "cus_003", serviceId: "svc_facial", staffId: "st_sana", date: addDays(baseToday, -42), amount: 1500, paymentStatus: "Paid", addonIds: [], completedAt: addDays(baseToday, -42) },
        { id: "visit_004", bookingId: "bk_008", customerId: "cus_010", serviceId: "svc_beard", staffId: "st_adeel", date: addDays(baseToday, -2), amount: 450, paymentStatus: "Paid", addonIds: [], completedAt: addDays(baseToday, -2) }
      ],
      walletTransactions: [
        { id: "wt_001", customerId: "cus_001", type: "Paid credit", paidCredit: 500, bonusCredit: 0, debit: 0, amount: 500, reason: "Wallet top-up confirmed", status: "Credited", createdAt: addDays(baseToday, -50) },
        { id: "wt_002", customerId: "cus_001", type: "Bonus credit", paidCredit: 0, bonusCredit: 50, debit: 0, amount: 50, reason: "Wallet top-up bonus", status: "Credited", createdAt: addDays(baseToday, -50) },
        { id: "wt_003", customerId: "cus_001", type: "Paid credit", paidCredit: 500, bonusCredit: 0, debit: 0, amount: 500, reason: "Wallet top-up confirmed", status: "Credited", createdAt: addDays(baseToday, -12) },
        { id: "wt_004", customerId: "cus_001", type: "Bonus credit", paidCredit: 0, bonusCredit: 70, debit: 0, amount: 70, reason: "Wallet top-up bonus", status: "Credited", createdAt: addDays(baseToday, -12) },
        { id: "wt_005", customerId: "cus_001", type: "Debit", paidCredit: -150, bonusCredit: -20, debit: 170, amount: -170, reason: "Visit bk_006", status: "Debited", createdAt: addDays(baseToday, -25) },
        { id: "wt_006", customerId: "cus_003", type: "Paid credit", paidCredit: 500, bonusCredit: 0, debit: 0, amount: 500, reason: "Wallet top-up confirmed", status: "Credited", createdAt: addDays(baseToday, -20) },
        { id: "wt_007", customerId: "cus_003", type: "Bonus credit", paidCredit: 0, bonusCredit: 50, debit: 0, amount: 50, reason: "Wallet top-up bonus", status: "Credited", createdAt: addDays(baseToday, -20) }
      ],
      reminders: [
        { id: "rem_001", customerId: "cus_001", serviceId: "svc_haircut", scheduledDate: baseToday, status: "Sent", bookingId: "bk_001", optOut: false },
        { id: "rem_002", customerId: "cus_003", serviceId: "svc_facial", scheduledDate: addDays(baseToday, 2), status: "Scheduled", bookingId: null, optOut: false },
        { id: "rem_003", customerId: "cus_005", serviceId: "svc_haircut", scheduledDate: addDays(baseToday, -3), status: "Opted out", bookingId: null, optOut: true }
      ],
      deposits: [{ id: "dep_demo_001", customerId: "cus_003", amount: 500, provider: "Easypaisa", reference: "EP-DEMO-500", proof: "Demo receipt attached", bonusAmount: 50, walletExpiryDays: 180, status: "Pending verification", submittedAt: now(), reviewedAt: null, reviewedBy: null, reason: "Customer-submitted manual deposit" }],
      withdrawals: [{ id: "wd_demo_001", customerId: "cus_001", amount: 200, provider: "JazzCash", destination: "03******567", status: "Pending", requestedAt: now(), updatedAt: now(), reason: "Demo customer withdrawal request", reservedAmount: 200 }],
      referrals: [{ id: "ref_001", referrerId: "cus_001", referredCustomerId: "cus_009", code: "AYAN100", status: "Pending visit", referrerReward: 100, newCustomerDiscount: 100, firstEligibleBookingId: null, completedVisitId: null, rewardedAt: null, createdAt: addDays(baseToday, -8) }],
      currentCustomerId: "cus_001",
      audit: [{ id: "audit_001", action: "Seeded test data", actor: "System", reason: "Initial setup", createdAt: now() }]
    };
  };
})(typeof window !== "undefined" ? window : globalThis);
