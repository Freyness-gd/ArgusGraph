package dev.argusgraph.graph.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.application.PackagePage;

/** Read response: one page of the package browse table, most-affected first. */
@Schema(name = "PackagePage")
public record PackagePageResponse(List<ItemResponse> items, @Schema(example = "0") int page,
		@Schema(example = "25") int size, @Schema(example = "312") long total) {

	public static PackagePageResponse from(PackagePage result) {
		return new PackagePageResponse(result.items().stream().map(ItemResponse::from).toList(), result.page(),
				result.size(), result.total());
	}

	/** One package row. */
	@Schema(name = "PackageListItem")
	public record ItemResponse(
			@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core") String packagePurl,
			@Schema(example = "maven") String type, @Schema(example = "7") long versionCount,
			@Schema(example = "3") long vulnerabilityCount) {

		static ItemResponse from(PackagePage.Item item) {
			return new ItemResponse(item.packagePurl(), item.type(), item.versionCount(), item.vulnerabilityCount());
		}
	}

}
