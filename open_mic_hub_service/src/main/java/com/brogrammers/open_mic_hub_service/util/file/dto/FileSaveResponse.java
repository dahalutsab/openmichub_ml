package com.brogrammers.open_mic_hub_service.util.file.dto;

import com.brogrammers.open_mic_hub_service.util.file.FileType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileSaveResponse {
    private String fileName;
    private String fileDownloadUri;
    private FileType fileType;
    private String fileDimension;
}
