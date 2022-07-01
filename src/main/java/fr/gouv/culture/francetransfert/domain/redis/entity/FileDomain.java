package fr.gouv.culture.francetransfert.domain.redis.entity;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@Builder
@NoArgsConstructor
public class FileDomain {
    private String path;
    private String fid;
    private String size;
}
