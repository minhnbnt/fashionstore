package com.minhnbnt.fashionstore.features.bank;

import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import com.minhnbnt.fashionstore.dto.bank.LookupAccountRequest;
import com.minhnbnt.fashionstore.dto.bank.LookupAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/banks")
@RequiredArgsConstructor
@Tag(
    name = "Banks",
    description = "Danh mục ngân hàng Việt Nam và tra cứu tài khoản Napas 24/7 qua VietQR. Công khai, không cần đăng nhập."
)
public class BankController {

    private final BankService bankService;

    @GetMapping
    @Operation(
        operationId = "listBanks",
        summary = "Danh sách ngân hàng Việt Nam",
        description = "Danh sách hơn 50 ngân hàng trong mạng lưới Napas kèm mã BIN, tên viết tắt và logo."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách ngân hàng")
    public List<BankResponse> listBanks() {
        return bankService.listBanks();
    }

    @PostMapping("/lookup-account")
    @Operation(
        operationId = "lookupAccount",
        summary = "Tra cứu tên chủ tài khoản Napas 24/7",
        description = "Tra cứu tên chủ tài khoản thật qua mã BIN ngân hàng và số tài khoản."
    )
    @ApiResponse(responseCode = "200", description = "Tra cứu thành công")
    @ApiResponse(responseCode = "400", description = "Số tài khoản hoặc mã BIN không hợp lệ / không tìm thấy")
    public LookupAccountResponse lookupAccount(@Valid @RequestBody LookupAccountRequest request) {
        return bankService.lookupAccount(request.bin(), request.accountNumber());
    }
}
