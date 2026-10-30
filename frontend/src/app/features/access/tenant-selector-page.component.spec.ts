import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TenantSelectorPageComponent } from './tenant-selector-page.component';

describe('TenantSelectorPageComponent', () => {
  let fixture: ComponentFixture<TenantSelectorPageComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TenantSelectorPageComponent],
      providers: [provideRouter([])],
    });
    fixture = TestBed.createComponent(TenantSelectorPageComponent);
    fixture.detectChanges();
  });

  it('links the two approved organizations to their direct staff login routes', () => {
    const host = fixture.nativeElement as HTMLElement;
    const options = Array.from(host.querySelectorAll<HTMLAnchorElement>('.tenant-option'));

    expect(options.map((option) => option.getAttribute('href'))).toEqual([
      '/login/veterinaria-aurora',
      '/login/veterinaria-bosque',
    ]);
    expect(options.map((option) => option.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      expect.stringContaining('Veterinaria Aurora'),
      expect.stringContaining('Veterinaria Bosque'),
    ]);
  });
});
