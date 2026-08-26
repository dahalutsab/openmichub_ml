package com.brogrammers.open_mic_hub_service.social_feed.dto;

import com.brogrammers.open_mic_hub_service.social_feed.entity.Posts;
import com.brogrammers.open_mic_hub_service.util.file.FileUrlUtil;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class PostResponse {
    private Long id;
    private String title;
    private String content;
    private List<URI> images;
    private Integer likesCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String author;
    private URI authorProfileImage;

    public PostResponse(Posts post) {
        this.id = post.getPostId();
        this.title = post.getTitle();
        this.content = post.getContent();
        this.images = getImagesList(post.getImages());
        this.likesCount = post.getLikesCount();
        this.createdAt = post.getCreatedDate();
        this.updatedAt = post.getLastModifiedDate();
        this.author = post.getArtist().getUser().getFullName();
        this.authorProfileImage = FileUrlUtil.getFileUri(post.getArtist().getUser().getProfileImage());
    }

    private List<URI> getImagesList(List<String> images) {
        List<URI> imageUris = new ArrayList<>();
        if (images != null) {
            for (String image : images) {
                URI imageUri = FileUrlUtil.getFileUri(image);
                imageUris.add(imageUri);
            }
        }
        return imageUris;
    }
}
