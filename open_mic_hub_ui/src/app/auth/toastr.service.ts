import { Injectable } from '@angular/core';
import { ToastrService } from 'ngx-toastr';

@Injectable({
  providedIn: 'root'
})
export class ToastService {

  constructor(private toast: ToastrService ) {}

  showSuccess(message: any){
    this.toast.success(message);
  }

  showError(message: any){
    this.toast.error(message);
  }

  showWarning(message: any){
    this.toast.warning(message);
  }

  showInfo(message: any){
    this.toast.info(message);
  }

  showShow(message: any){
    this.toast.show(message);
  }
}
