package com.minhnbnt.fashionstore.features.content.controller;

import com.minhnbnt.fashionstore.dto.content.PublicBannerResponse;
import com.minhnbnt.fashionstore.dto.content.PublicFaqResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPageLinkResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPageResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPopupResponse;
import com.minhnbnt.fashionstore.features.content.BannerService;
import com.minhnbnt.fashionstore.features.content.FaqService;
import com.minhnbnt.fashionstore.features.content.PopupService;
import com.minhnbnt.fashionstore.features.content.SitePageService;
import com.minhnbnt.fashionstore.model.BannerPlacement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Site content", description = "Banner, popup, trang tĩnh, FAQ cho storefront. Không cần đăng nhập.")
public class PublicContentController {

    private final BannerService banners;

    private final PopupService popups;

    private final SitePageService pages;

    private final FaqService faqs;

    @GetMapping("/banners")
    @Operation(
        operationId = "listBanners",
        summary = "Banner đang chạy theo vị trí",
        description = "Chỉ banner bật + trong khung giờ, theo thứ tự admin đặt."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách banner, rỗng nếu không có")
    @ApiResponse(responseCode = "400", description = "Vị trí không hợp lệ")
    public List<PublicBannerResponse> banners(
        @Parameter(description = "Vị trí banner", example = "HOME_CAROUSEL")
        @RequestParam BannerPlacement placement
    ) {
        return banners.running(placement);
    }

    @GetMapping("/popups")
    @Operation(
        operationId = "listPopups",
        summary = "Popup đang chạy",
        description = "Theo ưu tiên giảm dần. Trình duyệt lọc theo đối tượng + tần suất rồi hiện tối đa 1 popup."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách popup, rỗng nếu không có")
    public List<PublicPopupResponse> popups() {
        return popups.running();
    }

    @GetMapping("/pages")
    @Operation(
        operationId = "listPageLinks",
        summary = "Link trang tĩnh cho footer",
        description = "Trang đã xuất bản có nhóm footer, theo thứ tự admin đặt."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách link")
    public List<PublicPageLinkResponse> pageLinks() {
        return pages.publishedLinks();
    }

    @GetMapping("/pages/{slug}")
    @Operation(
        operationId = "getPage",
        summary = "Nội dung trang tĩnh",
        description = "Chỉ trang đã xuất bản."
    )
    @ApiResponse(responseCode = "200", description = "Nội dung trang")
    @ApiResponse(responseCode = "404", description = "Không có trang hoặc trang chưa xuất bản")
    public PublicPageResponse page(
        @Parameter(description = "slug trang", example = "chinh-sach-doi-tra") @PathVariable String slug
    ) {
        return pages.getPublished(slug);
    }

    @GetMapping("/faqs")
    @Operation(
        operationId = "listFaqs",
        summary = "Câu hỏi thường gặp",
        description = "FAQ đang hiện, theo nhóm rồi thứ tự."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách FAQ")
    public List<PublicFaqResponse> faqs() {
        return faqs.published();
    }
}
