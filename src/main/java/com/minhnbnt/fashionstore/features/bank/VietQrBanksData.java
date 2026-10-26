package com.minhnbnt.fashionstore.features.bank;

import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import java.util.List;

public final class VietQrBanksData {

    private VietQrBanksData() {}

    public static final List<BankResponse> DEFAULT_BANKS = List.of(
        new BankResponse("17", "Ngân hàng TMCP Ngoại Thương Việt Nam", "VCB", "970436", "Vietcombank", "https://api.vietqr.io/img/VCB.png", true),
        new BankResponse("21", "Ngân hàng TMCP Quân Đội", "MB", "970422", "MBBank", "https://api.vietqr.io/img/MB.png", true),
        new BankResponse("30", "Ngân hàng TMCP Kỹ Thương Việt Nam", "TCB", "970407", "Techcombank", "https://api.vietqr.io/img/TCB.png", true),
        new BankResponse("4", "Ngân hàng TMCP Đầu tư và Phát triển Việt Nam", "BIDV", "970418", "BIDV", "https://api.vietqr.io/img/BIDV.png", true),
        new BankResponse("43", "Ngân hàng TMCP Công Thương Việt Nam", "CTG", "970415", "VietinBank", "https://api.vietqr.io/img/ICB.png", true),
        new BankResponse("2", "Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam", "VBA", "970405", "Agribank", "https://api.vietqr.io/img/VBA.png", true),
        new BankResponse("1", "Ngân hàng TMCP Á Châu", "ACB", "970416", "ACB", "https://api.vietqr.io/img/ACB.png", true),
        new BankResponse("38", "Ngân hàng TMCP Việt Nam Thịnh Vượng", "VPB", "970432", "VPBank", "https://api.vietqr.io/img/VPB.png", true),
        new BankResponse("35", "Ngân hàng TMCP Tiên Phong", "TPB", "970423", "TPBank", "https://api.vietqr.io/img/TPB.png", true),
        new BankResponse("27", "Ngân hàng TMCP Sài Gòn Thương Tín", "STB", "970403", "Sacombank", "https://api.vietqr.io/img/STB.png", true),
        new BankResponse("12", "Ngân hàng TMCP Phát triển TP.HCM", "HDB", "970437", "HDBank", "https://api.vietqr.io/img/HDB.png", true),
        new BankResponse("37", "Ngân hàng TMCP Quốc tế Việt Nam", "VIB", "970441", "VIB", "https://api.vietqr.io/img/VIB.png", true),
        new BankResponse("29", "Ngân hàng TMCP Sài Gòn - Hà Nội", "SHB", "970443", "SHB", "https://api.vietqr.io/img/SHB.png", true),
        new BankResponse("28", "Ngân hàng TMCP Đông Nam Á", "SEAB", "970440", "SeABank", "https://api.vietqr.io/img/SEAB.png", true),
        new BankResponse("24", "Ngân hàng TMCP Phương Đông", "OCB", "970448", "OCB", "https://api.vietqr.io/img/OCB.png", true),
        new BankResponse("22", "Ngân hàng TMCP Hàng Hải", "MSB", "970426", "MSB", "https://api.vietqr.io/img/MSB.png", true),
        new BankResponse("18", "Ngân hàng TMCP Lộc Phát Việt Nam", "LPB", "970449", "LPBank", "https://api.vietqr.io/img/LPB.png", true),
        new BankResponse("23", "Ngân hàng TMCP Nam Á", "NAB", "970428", "Nam A Bank", "https://api.vietqr.io/img/NAB.png", true),
        new BankResponse("3", "Ngân hàng TMCP Bắc Á", "BAB", "970409", "Bac A Bank", "https://api.vietqr.io/img/BAB.png", true),
        new BankResponse("16", "Ngân hàng TMCP Kiên Long", "KLB", "970452", "Kienlongbank", "https://api.vietqr.io/img/KLB.png", true),
        new BankResponse("36", "Ngân hàng TMCP Bản Việt", "BVB", "970454", "BVBank", "https://api.vietqr.io/img/VCCB.png", true),
        new BankResponse("41", "Ngân hàng TMCP Việt Nam Thương Tín", "VBB", "970433", "VietBank", "https://api.vietqr.io/img/VIETBANK.png", true),
        new BankResponse("25", "Ngân hàng TMCP Đại Chúng Việt Nam", "PVCB", "970412", "PVcomBank", "https://api.vietqr.io/img/PVCOM.png", true),
        new BankResponse("31", "Ngân hàng TMCP Sài Gòn Công Thương", "SGB", "970400", "Saigonbank", "https://api.vietqr.io/img/SGICB.png", true),
        new BankResponse("5", "Ngân hàng TMCP Bảo Việt", "BVB", "970438", "BaoViet Bank", "https://api.vietqr.io/img/BVB.png", true),
        new BankResponse("26", "Ngân hàng TNHH MTV Public Việt Nam", "PBVN", "970439", "PublicBank", "https://api.vietqr.io/img/PBVN.png", true),
        new BankResponse("32", "Ngân hàng TNHH MTV Shinhan Việt Nam", "SHBVN", "970424", "Shinhan Bank", "https://api.vietqr.io/img/SHINHAN.png", true),
        new BankResponse("34", "Ngân hàng TNHH MTV Standard Chartered", "SCVN", "970410", "Standard Chartered", "https://api.vietqr.io/img/SCVN.png", true),
        new BankResponse("14", "Ngân hàng TNHH MTV Woori Việt Nam", "WOO", "970457", "Woori Bank", "https://api.vietqr.io/img/WOO.png", true),
        new BankResponse("6", "Ngân hàng TNHH MTV CIMB Việt Nam", "CIMB", "422589", "CIMB", "https://api.vietqr.io/img/CIMB.png", true),
        new BankResponse("40", "Ngân hàng TNHH MTV United Overseas Bank Việt Nam", "UOB", "970458", "UOB", "https://api.vietqr.io/img/UOB.png", true),
        new BankResponse("13", "Ngân hàng TNHH MTV Hong Leong Việt Nam", "HLBVN", "970442", "Hong Leong", "https://api.vietqr.io/img/HLBVN.png", true),
        new BankResponse("15", "Ngân hàng TNHH Indovina", "IVB", "970434", "Indovina Bank", "https://api.vietqr.io/img/IVB.png", true),
        new BankResponse("7", "Ngân hàng Hợp tác xã Việt Nam", "COOPBANK", "970446", "Co-op Bank", "https://api.vietqr.io/img/COOPBANK.png", true),
        new BankResponse("8", "Ngân hàng số CAKE by VPBank", "CAKE", "546034", "Cake", "https://api.vietqr.io/img/CAKE.png", true),
        new BankResponse("9", "Ngân hàng số Ubank by VPBank", "UBANK", "546035", "Ubank", "https://api.vietqr.io/img/UBANK.png", true),
        new BankResponse("33", "Ngân hàng số Timo by BVBank", "TIMO", "963388", "Timo", "https://api.vietqr.io/img/TIMO.png", true),
        new BankResponse("44", "Viettel Money", "VTLMONEY", "971005", "Viettel Money", "https://api.vietqr.io/img/VIETTELMONEY.png", true),
        new BankResponse("45", "VNPT Money", "VNPTMONEY", "971011", "VNPT Money", "https://api.vietqr.io/img/VNPTMONEY.png", true)
    );
}
