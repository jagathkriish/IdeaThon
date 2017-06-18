package com.vv.controller;

import java.sql.SQLException;
import java.util.Random;
import java.util.stream.Stream;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.vv.exception.StorageException;
import com.vv.model.Idea;
import com.vv.model.IdeaValidator;
import com.vv.model.Profile;
import com.vv.repositories.IdeaRepository;
import com.vv.repositories.ProfileRepository;
import com.vv.service.StorageService;
import com.vv.utils.FileNameBuilder;
import com.vv.validator.IdeaFileValidator;

@Controller
public class IdeaContoller {
	private final Logger log = LoggerFactory.getLogger(IdeaContoller.class);
	private StorageService storageService;
	private IdeaRepository ideaRepository;
	private ProfileRepository profileRepository;
	
	public IdeaContoller(StorageService storageService, IdeaRepository ideaRepository,ProfileRepository profileRepository) {
		this.storageService = storageService;
		this.ideaRepository = ideaRepository;
		this.profileRepository = profileRepository;
	}
	
	@Autowired
	private IdeaFileValidator ideaFileValidator;
	
	@InitBinder
	protected void initBinder(final WebDataBinder binder) {
	    binder.addValidators(ideaFileValidator);
	}
	
	@PostMapping("/registerIdea")
	public String checkPersonInfo(@Valid IdeaValidator ideaValidator, BindingResult bindingResult,
			RedirectAttributes redirectAttributes) {
		String videoFileName = "";
		String docFileName = "";
		String industry = "";
		String areaOfFunc = "";
		String buinvest = "";
		String buincome = "";
		String regStatus = "";
		
		if (bindingResult.hasErrors()) {
			return "ideaForm";
		}
		
		System.out.println(ideaValidator);
		
		docFileName = new FileNameBuilder().generateFileName(ideaValidator.getDocFile().getOriginalFilename(), ideaValidator.getCapId(), String.format("%04d", new Random().nextInt(10000))+"_d");
		if(ideaValidator.getVideoFile() != null && ideaValidator.getVideoFile().isEmpty()){
			videoFileName = "NA";
		}else{
			videoFileName = new FileNameBuilder().generateFileName(ideaValidator.getVideoFile().getOriginalFilename(),ideaValidator.getSolnTitle(),String.format("%04d", new Random().nextInt(10000))+"_v");
		}
		
		if(ideaValidator.getIndustry()!= null && ideaValidator.getIndustry().length != 0){
			for(int i=0;i<ideaValidator.getIndustry().length;i++){
				industry = industry+";"+ideaValidator.getIndustry()[i];
			}
		}else{
			industry = "NA";
		}
		
		//check other industries if any
 		if(ideaValidator.getoIndustry() != null){
 			industry +=  ";"+ideaValidator.getoIndustry();
 		}
		
		if(ideaValidator.getFuncArea()!= null && ideaValidator.getFuncArea().length != 0){
			for(int i=0;i<ideaValidator.getFuncArea().length;i++){
				areaOfFunc = areaOfFunc+";"+ideaValidator.getFuncArea()[i];
			}
		}else{
			areaOfFunc = "NA";
		}
		
		//check other function area if any
 		if(ideaValidator.getoFuncArea() != null){
 			areaOfFunc +=  ";"+ideaValidator.getoFuncArea();
 		}
		
		if(!ideaValidator.getBuinvest().equals(null)){
			buinvest = ideaValidator.getBuinvest();
		}else{
			buinvest = "NA";
		}
		
		if(!ideaValidator.getBuincome().equals(null)){
			buincome = ideaValidator.getBuincome();
		}else{
			buincome = "NA";
		}
		
		try{
			if(videoFileName != "NA"){
				storageService.storeIdea(ideaValidator.getDocFile(),docFileName,ideaValidator.getVideoFile(),videoFileName);
			}else{
				storageService.storeIdea(ideaValidator.getDocFile(),docFileName,null,"NA");
			}
			
			Profile profile = profileRepository.findOneByCapId(ideaValidator.getCapId());
			
			
			Profile profileBean = null;
			if(profile != null){
				profileBean = profile;
				System.out.println("old profile");
			}else{
				profileBean = new Profile(ideaValidator.getName(),ideaValidator.getCapId(), ideaValidator.getCapEmail(),ideaValidator.getContactNum(),ideaValidator.getServicebu(),ideaValidator.getProjectName(),ideaValidator.getLocationName()); 
				System.out.println("new profile");
			}
			
			if(ideaRepository.findByProfile(profileBean) != null){
				throw new SQLException("exists");
			}else{
				Stream<Idea> ideas = Stream.of(new Idea(profileBean,ideaValidator.getIdeaType(),ideaValidator.getProblemArea(), industry,areaOfFunc,ideaValidator.getTechnology(),ideaValidator.getSolnTitle(),ideaValidator.getSolnDesc(),ideaValidator.getBuBenift(),buinvest,buincome, docFileName,videoFileName,"initial",0));
				ideas.forEach(ideaRepository::save);
				regStatus = "You have successfully uploaded your idea";
				redirectAttributes.addFlashAttribute("style", "success");
			}
			
		}catch(Exception e){
			if(e.getClass().getName().contains("StorageException")){
				regStatus = "Please rename your file and select";
				redirectAttributes.addFlashAttribute("ideaValidator", ideaValidator);
			}else if(e.getClass().getName().contains("SQLException")){
				regStatus = "You have already registered your Idea";
			}else{
				regStatus = "We are sorry for this.Please register later";
			}
			
			redirectAttributes.addFlashAttribute("style", "error");
			e.printStackTrace();
		}
		
		redirectAttributes.addFlashAttribute("regStatus", regStatus);	
		return "redirect:/submitIdea";
		
	}
	
	@PostMapping(value = "/rateIdea")
	public ResponseEntity<?> rateIdea(@RequestParam String capId,@RequestParam Float exRtng,@RequestParam Float  rating,@RequestParam String status, @RequestParam String extSts){
		Profile profile = profileRepository.findOneByCapId(capId);
		System.out.println(profile);
		System.out.println(exRtng+"  "+rating);
		 		float finalRating = (exRtng + rating)/5;
		 		//verify current status, if empty keep existing status as is
		 		if(null == status || status.trim().equals("") || status.trim().equalsIgnoreCase("NA")){
		 			status = extSts;
		 		}
		 		
		 		int val = ideaRepository.rateIdea(finalRating, status.trim(), profile.getId());
		 		System.out.println(val);
		if(val == 1){
			return ResponseEntity.status(HttpStatus.OK).body(null);
		}else{
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
		}
		
	}
		
}