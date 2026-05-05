package com.example.inventory_system_ht.Helper;

import com.example.inventory_system_ht.Models.AuthModels;
import com.example.inventory_system_ht.Models.DOModels;
import com.example.inventory_system_ht.Models.GeneralResponse;
import com.example.inventory_system_ht.Models.ItemModels;
import com.example.inventory_system_ht.Models.LocationModels;
import com.example.inventory_system_ht.Models.StockInRequest;
import com.example.inventory_system_ht.Models.StockOutModels;
import com.example.inventory_system_ht.Models.StockPrepBulkRequest;
import com.example.inventory_system_ht.Models.StockTakingModels;
import com.example.inventory_system_ht.Models.TagModels;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    // ── Ping & Auth ───────────────────────────────────────────────

    @GET("api/ping")
    Call<GeneralResponse> ping();

    @POST("api/auth/login")
    Call<AuthModels.LoginResponse> login(@Body AuthModels.LoginRequest loginRequest);

    // ── Tag ───────────────────────────────────────────────────────

    @POST("api/tag/register")
    Call<GeneralResponse> registerTags(@Header("Authorization") String token,
                                       @Body AuthModels.RegisterRequest request);

    @GET("api/tag/{id}")
    Call<TagModels.TagModel> getTagDetail(@Header("Authorization") String token,
                                          @Path("id") String tagId);

    @GET("api/stockin/{code}")
    Call<TagModels.TagResponseDto> getTagByCode(@Header("Authorization") String token,
                                                @Path("code") String code,
                                                @Query("scannerType") String scannerType);

    @GET("api/stockin/{code}")
    Call<TagModels.TagInfoDto> getTagInfo(@Header("Authorization") String token,
                                          @Path("code") String code);

    // ── Stock In ─────────────────────────────────────────────────

    @POST("api/stockin")
    Call<GeneralResponse> stockIn(@Header("Authorization") String token,
                                  @Body StockInRequest request);

    // ── Stock Preparation ────────────────────────────────────────

    @POST("api/preparation/bulk")
    Call<GeneralResponse> submitStockPrep(@Header("Authorization") String token,
                                          @Body StockPrepBulkRequest request);

    @GET("api/preparation/do")
    Call<List<DOModels.DOModel>> getDo(@Header("Authorization") String token);

    @GET("api/pickinglist/{id}")
    Call<DOModels.DOResponseDto> getPickingListById(@Header("Authorization") String token,
                                                    @Path("id") String id);

    @GET("api/do")
    Call<List<DOModels.DOModel>> getAllDO(@Header("Authorization") String token);

    // ── Stock Taking (new endpoints) ──────────────────────────────

    /** Ambil sesi stock taking yang sedang OPEN */
    @GET("api/stock-taking/active")
    Call<StockTakingModels.ActiveRes> getActiveStockTaking(@Header("Authorization") String token);

    /** Ambil individual tags dalam snapshot sesi */
    @GET("api/stock-taking/tags/{sttId}")
    Call<List<StockTakingModels.SessionItem>> getSessionTags(@Header("Authorization") String token,
                                                             @Path("sttId") String sttId);

    /** Buat sesi baru */
    @POST("api/stock-taking")
    Call<StockTakingModels.CreateRes> createNewStockTaking(@Header("Authorization") String token,
                                                           @Body StockTakingModels.CreateReq request);

    /** Bulk scan (kirim semua EPC yang ditemukan sekaligus) */
    @POST("api/stock-taking/scan/bulk")
    Call<GeneralResponse> bulkScanStockTaking(@Header("Authorization") String token,
                                              @Body StockTakingModels.BulkScanReq request);

    /** Remove tag dari sesi */
    @POST("api/stock-taking/remove")
    Call<GeneralResponse> removeStockTaking(@Header("Authorization") String token,
                                            @Body StockTakingModels.RemoveReq request);

    /** Manual add item ke sesi */
    @POST("api/stock-taking/manual-add")
    Call<GeneralResponse> manualAddStockTaking(@Header("Authorization") String token,
                                               @Body StockTakingModels.ManualAddReq request);

    /** Apply adjustment dari HT (ganti finalize untuk Android) */
    @POST("api/stock-taking/apply-adjustment")
    Call<GeneralResponse> applyAdjustment(@Header("Authorization") String token,
                                          @Body StockTakingModels.FinalizeReq request);

    /** Finalize sesi (untuk admin web) */
    @POST("api/stock-taking/finalize")
    Call<GeneralResponse> finalizeStockTaking(@Header("Authorization") String token,
                                              @Body StockTakingModels.FinalizeReq request);

    // ── Stock Taking (legacy endpoints — tetap dipertahankan) ────

    @POST("api/stocktaking/create")
    Call<StockTakingModels.CreateRes> createStockTaking(@Header("Authorization") String token,
                                                        @Body StockTakingModels.CreateReq request);

    @GET("api/stocktaking/data")
    Call<List<TagModels.TagModel>> getStockData(@Header("Authorization") String token);

    @POST("api/stocktaking/scan")
    Call<GeneralResponse> scanStockTaking(@Header("Authorization") String token,
                                          @Body StockTakingModels.ScanReq request);

    @POST("api/stocktaking/apply-adjustment")
    Call<GeneralResponse> applyAdjustmentStockTaking(@Header("Authorization") String token, @Body StockTakingModels.FinalizeReq request);
    @POST("api/stocktaking/finalize")
    Call<GeneralResponse> finalizeStockTakingLegacy(@Header("Authorization") String token,
                                                    @Body StockTakingModels.FinalizeReq request);

    // ── Location & Item ──────────────────────────────────────────

    @GET("api/location")
    Call<List<LocationModels.LocationModel>> getLocations(@Header("Authorization") String token);

    @GET("api/item")
    Call<List<ItemModels.ItemResponseDto>> getAllItems(@Header("Authorization") String token);

    // ── Search Item ──────────────────────────────────────────────

    @GET("/api/search-item")
    Call<List<TagModels.SearchItemListDto>> getSearchItems(@Header("Authorization") String token);

    @GET("/api/search-item/{code}")
    Call<TagModels.TagDetailDto> getTagDetailSearchItem(@Header("Authorization") String token,
                                                        @Path("code") String code);
}