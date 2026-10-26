# SDD ledger — plan: docs/superpowers/plans/2026-10-26-seller-address-and-napas-bank-lookup.md

Pre-flight: no shared interfaces conflict

- [x] Task 1: Backend DTOs & Config for VietQR Napas Lookup (Commit `8db54c6`)
- [x] Task 2: Backend BankService & 55+ Static Banks Fallback (Commit `c588bfc`)
- [x] Task 3: Backend BankController & OpenAPI Export (Commit `fa7e1f5`)
- [x] Task 4: Frontend OpenAPI Codegen & Bank Types (Commit `1489671`)
- [x] Task 5: Frontend LocationAddressSelector Component (Commit `eaa4fa0`)
- [x] Task 6: Frontend BankSearchSelector & Search Utility (Commits `56a91dc`, `83ebce2`)
- [x] Task 7: Integrate Address & Napas Bank Lookup into ShopKycRegistrationModal (Commit `4a8e3c5`)
- [x] Task 8: Integrate LocationAddressSelector into SellerProfileTab (Commit `58dc466`)
- [x] Task 9: Comprehensive Verification & Lint Optimization (Commits `d5d8073`, `389fa9b`):
  - Backend: `.\gradlew test` -> BUILD SUCCESSFUL in 2m 55s (100% tests passed)
  - Frontend Lint: `pnpm lint` -> 0 warnings, 0 errors
  - Frontend Unit Tests: `pnpm test` -> 28 test files passed (229/229 tests passed)
  - Frontend Production Build: `pnpm build` -> built in 1.93s, TypeScript check passed
