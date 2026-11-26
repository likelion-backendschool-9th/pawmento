package com.pawmento.community.application.service.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pawmento.community.application.exception.DuplicateReportException;
import com.pawmento.community.application.exception.PostNotFoundException;
import com.pawmento.community.application.port.in.report.ReportPostCommand;
import com.pawmento.community.application.port.in.report.ReportPostUseCase;
import com.pawmento.community.application.port.out.LoadPostPort;
import com.pawmento.community.application.port.out.report.LoadLatestReportPort;
import com.pawmento.community.application.port.out.report.SaveReportPort;
import com.pawmento.community.domain.Post;
import com.pawmento.community.domain.report.Report;

import lombok.RequiredArgsConstructor;

// 글을 한 번 읽어 스냅샷을 만들고 같은 트랜잭션에서 저장한다. 글 행은 잠그지 않는다.
// 읽은 뒤 작성자가 글을 고치거나 지워도 읽은 시점의 스냅샷으로 접수한다(#112-S13)
@Service
@RequiredArgsConstructor
public class ReportPostService implements ReportPostUseCase {

	private final LoadPostPort loadPostPort;
	private final LoadLatestReportPort loadLatestReportPort;
	private final SaveReportPort saveReportPort;

	@Override
	@Transactional
	public Long report(ReportPostCommand command) {
		// 없는 글과 지운 글은 공개 상세와 같이 404다
		Post post = loadPostPort.findById(command.postId()).orElseThrow(PostNotFoundException::new);
		Report report = Report.receive(post, command.memberId(), command.reasonType(), command.reasonDetail());
		// 재신고 판정은 그 회원의 가장 최근 신고만 본다(#112-S4)
		loadLatestReportPort.findLatest(post.getId(), command.memberId())
			.filter(previous -> !previous.allowsNewReportOn(post.getVersion()))
			.ifPresent(previous -> {
				throw new DuplicateReportException();
			});
		// 확인과 저장 사이에 같은 요청이 끼어들면 저장소가 PENDING 부분 고유 인덱스로 막는다(#112-S5)
		return saveReportPort.save(report);
	}

}
