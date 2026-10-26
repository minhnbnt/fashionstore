package com.minhnbnt.fashionstore.features.bank;

import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import java.util.List;

public final class VietQrBanksData {

    private VietQrBanksData() {}

    private static BankResponse bank(
        String id,
        String name,
        String code,
        String bin,
        String shortName,
        String logo
    ) {
        return new BankResponse(id, name, code, bin, shortName, "https://api.vietqr.io/img/" + logo, true);
    }

    public static final List<BankResponse> DEFAULT_BANKS = List.of(
        bank("17", "Ngân hàng TMCP Ngoại Thương Việt Nam", "VCB", "970436", "Vietcombank", "VCB.png"),
        bank("21", "Ngân hàng TMCP Quân Đội", "MB", "970422", "MBBank", "MB.png"),
        bank("30", "Ngân hàng TMCP Kỹ Thương Việt Nam", "TCB", "970407", "Techcombank", "TCB.png"),
        bank("4", "Ngân hàng TMCP Đầu tư và Phát triển Việt Nam", "BIDV", "970418", "BIDV", "BIDV.png"),
        bank("43", "Ngân hàng TMCP Công Thương Việt Nam", "CTG", "970415", "VietinBank", "ICB.png"),
        bank("2", "Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam", "VBA", "970405", "Agribank", "VBA.png"),
        bank("1", "Ngân hàng TMCP Á Châu", "ACB", "970416", "ACB", "ACB.png"),
        bank("38", "Ngân hàng TMCP Việt Nam Thịnh Vượng", "VPB", "970432", "VPBank", "VPB.png"),
        bank("35", "Ngân hàng TMCP Tiên Phong", "TPB", "970423", "TPBank", "TPB.png"),
        bank("27", "Ngân hàng TMCP Sài Gòn Thương Tín", "STB", "970403", "Sacombank", "STB.png"),
        bank("12", "Ngân hàng TMCP Phát triển TP.HCM", "HDB", "970437", "HDBank", "HDB.png"),
        bank("37", "Ngân hàng TMCP Quốc tế Việt Nam", "VIB", "970441", "VIB", "VIB.png"),
        bank("29", "Ngân hàng TMCP Sài Gòn - Hà Nội", "SHB", "970443", "SHB", "SHB.png"),
        bank("28", "Ngân hàng TMCP Đông Nam Á", "SEAB", "970440", "SeABank", "SEAB.png"),
        bank("24", "Ngân hàng TMCP Phương Đông", "OCB", "970448", "OCB", "OCB.png"),
        bank("22", "Ngân hàng TMCP Hàng Hải", "MSB", "970426", "MSB", "MSB.png"),
        bank("18", "Ngân hàng TMCP Lộc Phát Việt Nam", "LPB", "970449", "LPBank", "LPB.png"),
        bank("23", "Ngân hàng TMCP Nam Á", "NAB", "970428", "Nam A Bank", "NAB.png"),
        bank("3", "Ngân hàng TMCP Bắc Á", "BAB", "970409", "Bac A Bank", "BAB.png"),
        bank("16", "Ngân hàng TMCP Kiên Long", "KLB", "970452", "Kienlongbank", "KLB.png"),
        bank("36", "Ngân hàng TMCP Bản Việt", "BVB", "970454", "BVBank", "VCCB.png"),
        bank("41", "Ngân hàng TMCP Việt Nam Thương Tín", "VBB", "970433", "VietBank", "VIETBANK.png"),
        bank("25", "Ngân hàng TMCP Đại Chúng Việt Nam", "PVCB", "970412", "PVcomBank", "PVCOM.png"),
        bank("31", "Ngân hàng TMCP Sài Gòn Công Thương", "SGB", "970400", "Saigonbank", "SGICB.png"),
        bank("5", "Ngân hàng TMCP Bảo Việt", "BVB", "970438", "BaoViet Bank", "BVB.png"),
        bank("26", "Ngân hàng TNHH MTV Public Việt Nam", "PBVN", "970439", "PublicBank", "PBVN.png"),
        bank("32", "Ngân hàng TNHH MTV Shinhan Việt Nam", "SHBVN", "970424", "Shinhan Bank", "SHINHAN.png"),
        bank("34", "Ngân hàng TNHH MTV Standard Chartered", "SCVN", "970410", "Standard Chartered", "SCVN.png"),
        bank("14", "Ngân hàng TNHH MTV Woori Việt Nam", "WOO", "970457", "Woori Bank", "WOO.png"),
        bank("6", "Ngân hàng TNHH MTV CIMB Việt Nam", "CIMB", "422589", "CIMB", "CIMB.png"),
        bank("40", "Ngân hàng TNHH MTV United Overseas Bank Việt Nam", "UOB", "970458", "UOB", "UOB.png"),
        bank("13", "Ngân hàng TNHH MTV Hong Leong Việt Nam", "HLBVN", "970442", "Hong Leong", "HLBVN.png"),
        bank("15", "Ngân hàng TNHH Indovina", "IVB", "970434", "Indovina Bank", "IVB.png"),
        bank("7", "Ngân hàng Hợp tác xã Việt Nam", "COOPBANK", "970446", "Co-op Bank", "COOPBANK.png"),
        bank("8", "Ngân hàng số CAKE by VPBank", "CAKE", "546034", "Cake", "CAKE.png"),
        bank("9", "Ngân hàng số Ubank by VPBank", "UBANK", "546035", "Ubank", "UBANK.png"),
        bank("33", "Ngân hàng số Timo by BVBank", "TIMO", "963388", "Timo", "TIMO.png"),
        bank("44", "Viettel Money", "VTLMONEY", "971005", "Viettel Money", "VIETTELMONEY.png"),
        bank("45", "VNPT Money", "VNPTMONEY", "971011", "VNPT Money", "VNPTMONEY.png")
    );
}
