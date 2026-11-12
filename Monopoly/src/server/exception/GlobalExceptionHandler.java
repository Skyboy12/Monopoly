package server.exception;

import network.GameMessage;
import server.dto.ApiErrorResponse;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bộ xử lý ngoại lệ tập trung và ghi log lỗi nghiệp vụ/hệ thống.
 */
public class GlobalExceptionHandler {
    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionHandler.class.getName());

    public static GameMessage handleException(String roomId, String senderId, int turnId, Throwable ex) {
        if (ex instanceof IllegalArgumentException) {
            LOGGER.log(Level.WARNING, "[VALIDATION_ERROR] room={0}, user={1}: {2}",
                    new Object[]{roomId, senderId, ex.getMessage()});
            ApiErrorResponse err = new ApiErrorResponse("BAD_REQUEST", ex.getMessage(), roomId, turnId);
            return new GameMessage("ERROR", senderId, roomId, turnId, err.toJson());
        }

        if (ex instanceof IllegalStateException) {
            LOGGER.log(Level.WARNING, "[BUSINESS_CONFLICT] room={0}, user={1}: {2}",
                    new Object[]{roomId, senderId, ex.getMessage()});
            ApiErrorResponse err = new ApiErrorResponse("CONFLICT", ex.getMessage(), roomId, turnId);
            return new GameMessage("ERROR", senderId, roomId, turnId, err.toJson());
        }

        LOGGER.log(Level.SEVERE, "[INTERNAL_ERROR] Lỗi không xác định tại room=" + roomId, ex);
        ApiErrorResponse err = new ApiErrorResponse(
                "INTERNAL_SERVER_ERROR",
                "Đã xảy ra lỗi hệ thống, vui lòng thử lại.",
                roomId,
                turnId
        );
        return new GameMessage("ERROR", senderId, roomId, turnId, err.toJson());
    }
}