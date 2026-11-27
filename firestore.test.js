const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_owner_2";
const ADMIN_UID = "admin_user_1";
const ADMIN_EMAIL = "truth.tellerq@gmail.com";
const BIZ_ID = "biz_sharma_kirana";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

function sampleBusiness(userId) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    name: "Sharma Kirana Store",
    ownerName: "Rajesh Sharma",
    phone: "9876543210",
    businessType: "Kirana",
    city: "Jaipur",
    state: "Rajasthan",
    preferredLang: "hinglish",
    currency: "₹",
    fyStart: "April",
    gstin: "",
    planId: "trial",
    subscriptionStatus: "trialing",
    trialStartMillis: Date.now() - 10000,
    trialEndMillis: Date.now() + 7 * 86400000,
    negativeStockProtection: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write any business or plan", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("plans").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("businesses").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("businesses").doc(BIZ_ID).set(sampleBusiness(ALICE_UID))
  );
});

test("Owner can create business, branch, customer, immutable khata entry, product, sale & return", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bizRef = aliceDb.collection("users").doc(ALICE_UID).collection("businesses").doc(BIZ_ID);
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(bizRef.set(sampleBusiness(ALICE_UID)));

  // Reject ghost field / shadow update
  await assertFails(bizRef.update({ isVerified: true, updatedAt: new Date(Date.now() - 500) }));

  // Branch create
  await assertSucceeds(
    bizRef.collection("branches").doc("br_main").set({
      userId: ALICE_UID,
      businessId: BIZ_ID,
      name: "Main Branch",
      city: "Jaipur",
      primaryBranch: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Customer create
  await assertSucceeds(
    bizRef.collection("customers").doc("cust_mohan").set({
      userId: ALICE_UID,
      businessId: BIZ_ID,
      branchId: "br_main",
      name: "Mohan Lal",
      phone: "9988776655",
      openingBalance: 0,
      overdueDays: 15,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Khata entry create (immutable)
  const khataRef = bizRef.collection("khataEntries").doc("kh_1");
  await assertSucceeds(
    khataRef.set({
      userId: ALICE_UID,
      businessId: BIZ_ID,
      branchId: "br_main",
      customerId: "cust_mohan",
      customerName: "Mohan Lal",
      type: "credit",
      amount: 500,
      note: "Atta 10kg",
      date: "2026-11-27",
      idempotencyKey: "idem_1",
      createdAt: now,
    })
  );
  // Khata entry cannot be mutated or deleted (must use reversal entry)
  await assertFails(khataRef.update({ amount: 100 }));
  await assertFails(khataRef.delete());

  // Product create & update
  const prodRef = bizRef.collection("products").doc("prd_atta");
  await assertSucceeds(
    prodRef.set({
      userId: ALICE_UID,
      businessId: BIZ_ID,
      branchId: "br_main",
      name: "Aashirvaad Atta 10kg",
      sku: "ATT01",
      unit: "pkt",
      costPrice: 380,
      sellPrice: 430,
      taxRate: 0,
      stock: 20,
      lowStockThreshold: 5,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(prodRef.update({ stock: 19, updatedAt: new Date(Date.now() - 500) }));

  // Sale create & return terminal lock
  const saleRef = bizRef.collection("sales").doc("sale_1");
  await assertSucceeds(
    saleRef.set({
      userId: ALICE_UID,
      businessId: BIZ_ID,
      branchId: "br_main",
      invoiceNo: "INV-0001",
      date: "2026-11-27",
      status: "completed",
      items: [{ productId: "prd_atta", name: "Aashirvaad Atta 10kg", unit: "pkt", qty: 1, price: 430, costPrice: 380, taxRate: 0 }],
      subtotal: 430,
      tax: 0,
      discount: 0,
      total: 430,
      totalCost: 380,
      paymentMode: "Cash",
      paidNow: 430,
      udhaarAmount: 0,
      customerName: "Walk-in Customer",
      idempotencyKey: "sale_idem_1",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(saleRef.update({ status: "returned", updatedAt: new Date(Date.now() - 500) }));
  // Terminal state lock: returned sale cannot be updated again
  await assertFails(saleRef.update({ status: "completed", updatedAt: new Date(Date.now() - 300) }));
});

test("Cross-user isolation: Bob cannot read or write Alice's business or khata", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("businesses").doc(BIZ_ID).set(sampleBusiness(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businesses").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businesses").doc(BIZ_ID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("businesses").doc(BIZ_ID).update({ name: "Hacked" }));
});

test("Super Admin verified email enforces RBAC for plans; unverified spoofed email fails", async () => {
  const spoofedAdminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: false }).firestore();
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true }).firestore();
  const normalUserDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();

  const planPayload = {
    code: "starter",
    name: "Starter",
    price: 200,
    billingDays: 30,
    badge: "Habit Builder",
    sortOrder: 1,
    highlights: ["Includes Sakha AI", "Single Customer Udhaar Khata"],
    updatedAt: new Date(Date.now() - 1000),
  };

  await assertFails(spoofedAdminDb.collection("plans").doc("starter").set(planPayload));
  await assertFails(normalUserDb.collection("plans").doc("starter").set(planPayload));
  await assertSucceeds(realAdminDb.collection("plans").doc("starter").set(planPayload));
  await assertSucceeds(normalUserDb.collection("plans").get());
});
