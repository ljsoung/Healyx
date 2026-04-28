class Hospital {
  final String hospitalName;
  final String address;
  final double rating;
  final bool hasBadge;
  final bool hasReview;

  const Hospital({
    required this.hospitalName,
    required this.address,
    required this.rating,
    required this.hasBadge,
    required this.hasReview,
  });

  // JSON에서 객체로 변환 (API 연동 시 사용)
  factory Hospital.fromJson(Map<String, dynamic> json) {
    return Hospital(
      hospitalName: json['hospitalName'] as String,
      address: json['address'] as String,
      rating: (json['rating'] as num).toDouble(),
      hasBadge: json['hasBadge'] as bool? ?? false,
      hasReview: json['hasReview'] as bool? ?? false,
    );
  }

  // 객체를 JSON으로 변환
  Map<String, dynamic> toJson() {
    return {
      'hospitalName': hospitalName,
      'address': address,
      'rating': rating,
      'hasBadge': hasBadge,
      'hasReview': hasReview,
    };
  }
}
